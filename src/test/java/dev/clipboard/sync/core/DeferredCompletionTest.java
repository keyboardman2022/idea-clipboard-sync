package dev.clipboard.sync.core;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class DeferredCompletionTest {
    @Test void busyBackgroundWorkDefersRatherThanDropsCompletion(){
        AtomicBoolean busy=new AtomicBoolean(true);AtomicInteger delivered=new AtomicInteger();
        var job=new DeferredCompletion(()->!busy.get(),()->true,delivered::incrementAndGet);
        assertFalse(job.tick());assertFalse(job.tick());assertEquals(0,delivered.get());
        busy.set(false);assertTrue(job.tick());assertTrue(job.tick());assertEquals(1,delivered.get());
    }
    @Test void closedOrReplacedReceiveSessionCannotDisplayOldPreview(){
        AtomicBoolean relevant=new AtomicBoolean(true);AtomicInteger delivered=new AtomicInteger();
        var job=new DeferredCompletion(()->false,relevant::get,delivered::incrementAndGet);
        assertFalse(job.tick());relevant.set(false);assertTrue(job.tick());assertEquals(0,delivered.get());
    }
}
