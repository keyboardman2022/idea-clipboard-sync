package dev.clipboard.sync.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoTransferTest {
    @TempDir Path temp;
    List<String> chunks(int size) throws Exception {
        byte[] bytes=new byte[size];new Random(41).nextBytes(bytes);
        var snapshot=new Snapshot(UUID.randomUUID(),"source",Instant.now(),List.of(new FileEntry("sub/data.bin",bytes,CoreIo.sha256(bytes))));
        return ClipboardChunks.split(snapshot.transferId(),SnapshotCodec.encode(snapshot,Limits.defaults()),4096);
    }
    AutoTransfer.Receiver receiver() { return new AutoTransfer.Receiver(new ReceiveStore(temp,Limits.defaults())); }
    String initialData(AutoTransfer.Sender sender,AutoTransfer.Receiver receiver) throws Exception {
        String hello=sender.tick("",0);assertTrue(hello.startsWith(AutoTransfer.PREFIX+"|HELLO|"));
        String ready=receiver.tick(hello,0);assertTrue(ready.startsWith(AutoTransfer.PREFIX+"|READY|"));
        return sender.tick(ready,0);
    }
    @Test void automaticallyTransfersMoreThan300SegmentsAndRecoversLostAcknowledgement() throws Exception {
        var chunks=chunks(950_000);assertTrue(chunks.size()>300);
        var sender=new AutoTransfer.Sender(chunks,100,4);var receiver=receiver();String clipboard="existing user text";boolean lost=false;int writes=0;long dropUntil=-1;
        for(long now=0;now<500_000 && !sender.progress().complete();now+=20){
            String data=sender.tick(clipboard,now);if(data!=null){clipboard=data;writes++;}
            String ack=receiver.tick(clipboard,now);
            if(ack!=null){if(!lost && receiver.progress().confirmed()==7){lost=true;dropUntil=now+120;}if(now>=dropUntil)clipboard=ack;}
        }
        assertTrue(sender.progress().complete(),sender.progress().message());assertTrue(receiver.progress().complete());assertTrue(lost);assertTrue(writes>chunks.size()+1);
        assertEquals(chunks.size(),sender.progress().confirmed());assertEquals("sub/data.bin",receiver.snapshot().files().getFirst().relativePath());
        assertEquals(950_000,receiver.snapshot().files().getFirst().size());
    }
    @Test void neverAdvancesWithoutCorrectRunAndChunkAcknowledgement() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(8000),100,3);var receiver=receiver();
        String data=initialData(sender,receiver),ack=receiver.tick(data,0);
        String wrong=ack.replace(sender.runId().toString(),UUID.randomUUID().toString());
        sender.tick(wrong,20);assertEquals(0,sender.progress().confirmed());
        sender.tick(ack,40);assertEquals(1,sender.progress().confirmed());
    }
    @Test void retriesPauseAfterLimitAndCanResumeWithoutSkippingCurrentSegment() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(100),100,2);
        String first=initialData(sender,receiver());assertNull(sender.tick(first,50));assertEquals(first,sender.tick(first,100));
        assertNull(sender.tick(first,200));assertTrue(sender.progress().paused());assertEquals(0,sender.progress().confirmed());
        sender.resume();assertEquals(first,sender.tick(first,250));assertFalse(sender.progress().paused());
    }
    @Test void userClipboardContentPausesInsteadOfBeingOverwritten() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(100),100,3);initialData(sender,receiver());
        assertNull(sender.tick("a password or other user copy",20));assertTrue(sender.progress().paused());
        var receiver=receiver();assertNull(receiver.tick("ordinary clipboard",0));assertFalse(receiver.progress().paused());
    }
    @Test void receiverKeepsAcknowledgingLastSegmentAfterCompletion() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(100),100,3);var receiver=receiver();String frame=initialData(sender,receiver);
        String ack=receiver.tick(frame,0);assertTrue(receiver.progress().complete());assertEquals(ack,receiver.tick(frame,500));
        sender.tick(ack,500);assertTrue(sender.progress().complete());
    }
    @Test void pauseAndResumeReceiverRetainsReceivedParts() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(8000),100,4);var receiver=receiver();String first=initialData(sender,receiver);String ack=receiver.tick(first,0);
        String next=sender.tick(ack,20);receiver.pause("user pause");assertNull(receiver.tick(next,40));receiver.resume();
        receiver.tick(next,60);assertEquals(2,receiver.progress().confirmed());
    }
    @Test void corruptedDataIsNotAcknowledgedAndOriginalCanBeRetried() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(100),100,4);var receiver=receiver();String frame=initialData(sender,receiver);
        String damaged=frame.substring(0,frame.length()-1)+"!";
        assertNull(receiver.tick(damaged,0));assertFalse(receiver.progress().complete());assertEquals(0,receiver.progress().confirmed());
        String resent=sender.tick(frame,100);assertNotNull(receiver.tick(resent,100));assertTrue(receiver.progress().complete());
    }
    @Test void receiverDoesNotMixAnotherActiveTransfer() throws Exception {
        var a=new AutoTransfer.Sender(chunks(8000),100,4);var b=new AutoTransfer.Sender(chunks(8000),100,4);var receiver=receiver();
        receiver.tick(initialData(a,receiver),0);assertNull(receiver.tick(b.tick("",0),10));assertTrue(receiver.progress().paused());assertEquals(1,receiver.progress().confirmed());
    }
    @Test void refusesCompletionUntilWholeSnapshotDecodes() throws Exception {
        var chunks=ClipboardChunks.split(UUID.randomUUID(),new byte[]{1,2,3},4096);
        var sender=new AutoTransfer.Sender(chunks,100,4);var receiver=receiver();
        String data=initialData(sender,receiver);
        assertThrows(java.io.IOException.class,()->receiver.tick(data,0));
        assertFalse(receiver.progress().complete());assertFalse(sender.progress().complete());
    }
    @Test void cachedProgressCannotHideDiskCorruptionAtFinalAssembly() throws Exception {
        var chunks=chunks(8000);var store=new ReceiveStore(temp,Limits.defaults());
        var first=ClipboardChunks.parse(chunks.getFirst());store.accept(chunks.getFirst());
        java.nio.file.Files.writeString(temp.resolve(first.id().toString()).resolve("1.part"),"corrupted");
        for(int i=1;i<chunks.size();i++)store.accept(chunks.get(i));
        assertThrows(java.io.IOException.class,()->store.assemble(first.id()));
    }
    @Test void restartDoesNotBindToStaleDataFromAbandonedRun() throws Exception {
        var chunks=chunks(8000);var abandoned=new AutoTransfer.Sender(chunks,100,4);String stale=initialData(abandoned,receiver());
        var fresh=receiver();assertNull(fresh.tick(stale,20));assertEquals(0,fresh.progress().confirmed());
        var restarted=new AutoTransfer.Sender(chunks,100,4);String hello=restarted.tick(stale,40);String ready=fresh.tick(hello,40);
        String data=restarted.tick(ready,60);assertNotNull(fresh.tick(data,60));assertFalse(fresh.progress().paused());assertEquals(1,fresh.progress().confirmed());
    }
    @Test void staleHelloCanBeReplacedBeforeAnyDataIsAccepted() throws Exception {
        var receiver=receiver();var stale=new AutoTransfer.Sender(chunks(100),100,4);receiver.tick(stale.tick("",0),0);
        var fresh=new AutoTransfer.Sender(chunks(100),100,4);String ready=receiver.tick(fresh.tick("",20),20);
        assertNotNull(receiver.tick(fresh.tick(ready,40),40));assertTrue(receiver.progress().complete());
    }
    @Test void delayedReadyDoesNotPauseAnAlreadyStartedSender() throws Exception {
        var sender=new AutoTransfer.Sender(chunks(8000),100,4);var receiver=receiver();
        String hello=sender.tick("",0),ready=receiver.tick(hello,0),data=sender.tick(ready,10);
        assertNull(sender.tick(ready,20));assertFalse(sender.progress().paused());assertEquals(0,sender.progress().confirmed());
        sender.tick(receiver.tick(data,30),40);assertEquals(1,sender.progress().confirmed());
    }
}
