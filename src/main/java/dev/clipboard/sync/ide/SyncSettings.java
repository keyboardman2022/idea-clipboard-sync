package dev.clipboard.sync.ide;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import java.util.*;

@State(name = "ClipboardDirectorySync", storages = @Storage("clipboard-directory-sync.xml"))
@Service(Service.Level.PROJECT)
public final class SyncSettings implements PersistentStateComponent<SyncSettings> {
    public int chunkChars = 65_536;
    public List<String> ignores = new ArrayList<>();
    @Override public SyncSettings getState() { return this; }
    @Override public void loadState(@NotNull SyncSettings state) { XmlSerializerUtil.copyBean(state, this); chunkChars = Math.max(4096, Math.min(1 << 20, chunkChars)); if (ignores == null) ignores = new ArrayList<>(); }
}
