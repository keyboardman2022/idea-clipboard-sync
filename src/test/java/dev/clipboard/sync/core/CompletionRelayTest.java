package dev.clipboard.sync.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.clipboard.sync.core.SyncModel.*;

class CompletionRelayTest {
    @Test void cancellationDeliversPartialMutationResultExactlyOnce() {
        List<ApplyResult> delivered=new ArrayList<>();var relay=new CompletionRelay<ApplyResult>(delivered::add);
        var result=new ApplyResult(List.of("already-written"),Map.of(),List.of("not-written"));
        relay.complete(result,true);relay.complete(result,false);
        assertEquals(1,delivered.size());assertEquals(List.of("already-written"),delivered.getFirst().succeeded());assertEquals(List.of("not-written"),delivered.getFirst().pending());
    }
    @Test void cancelledNonMutationDoesNotOpenSendDialogOrCopyData() {
        List<String> delivered=new ArrayList<>();var relay=new CompletionRelay<String>(delivered::add);
        relay.complete("encoded data",true);assertTrue(delivered.isEmpty());
    }
    @Test void normalSuccessDeliversOnceAndNullIsNotDelivered() {
        List<String> delivered=new ArrayList<>();var relay=new CompletionRelay<String>(delivered::add);
        relay.complete(null,true);relay.complete("ready",false);relay.complete("duplicate",false);
        assertEquals(List.of("ready"),delivered);
    }
}
