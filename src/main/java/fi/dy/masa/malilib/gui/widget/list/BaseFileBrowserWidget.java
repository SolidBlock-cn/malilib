package fi.dy.masa.malilib.gui.widget.list;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.lang3.tuple.Pair;
import fi.dy.masa.malilib.MaLiLibConfigs;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.ConfirmActionScreen;
import fi.dy.masa.malilib.gui.TextInputScreen;
import fi.dy.masa.malilib.gui.icon.DefaultFileBrowserIconProvider;
import fi.dy.masa.malilib.gui.icon.FileBrowserIconProvider;
import fi.dy.masa.malilib.gui.util.GuiUtils;
import fi.dy.masa.malilib.gui.widget.MenuEntryWidget;
import fi.dy.masa.malilib.gui.widget.MenuWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.entry.DirectoryEntryWidget;
import fi.dy.masa.malilib.gui.widget.list.header.ColumnizedDataListHeaderWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DataColumn;
import fi.dy.masa.malilib.gui.widget.list.header.DataListHeaderWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;
import fi.dy.masa.malilib.gui.widget.util.DirectoryCache;
import fi.dy.masa.malilib.gui.widget.util.DirectoryNavigator;
import fi.dy.masa.malilib.input.Keys;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.render.text.StyledText;
import fi.dy.masa.malilib.render.text.StyledTextLine;
import fi.dy.masa.malilib.util.DataIteratingTask;
import fi.dy.masa.malilib.util.DirectoryCreator;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.malilib.util.FileUtils;

public class BaseFileBrowserWidget extends DataListWidget<DirectoryEntry> implements DirectoryNavigator
{
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected final Map<Pair<Path, Predicate<Path>>, List<Path>> directoryContentsCache = new HashMap<>();
    protected final Object2IntOpenHashMap<Path> keyboardNavigationPositions = new Object2IntOpenHashMap<>();
    protected final Object2IntOpenHashMap<Path> scrollPositions = new Object2IntOpenHashMap<>();
    protected final Set<Path> operatedOnFiles = new HashSet<>();
    protected final DirectoryNavigationWidget navigationWidget;
    protected final Path rootDirectory;
    @Nullable protected final DirectoryCache cache;
    @Nullable protected String rootDirectoryDisplayName;
    protected Predicate<Path> directoryFilter = FileUtils.DIRECTORY_FILTER;
    protected Predicate<Path> fileFilter = FileUtils.ALWAYS_FALSE_FILEFILTER;
    protected String browserContext;
    protected Path currentDirectory;
    protected boolean allowFileOperations;
    protected boolean pendingOperationIsCut;
    protected boolean rememberScrollPosition;
    protected boolean shouldStoreKeyboardNavigationPosition = true;
    protected boolean showFileSize;
    protected boolean showFileModificationTime;
    protected boolean showHiddenFiles;

    public BaseFileBrowserWidget(Path defaultDirectory,
                                 Path rootDirectory,
                                 @Nullable DirectoryCache cache,
                                 @Nullable String browserContext)
    {
        this(defaultDirectory, rootDirectory, cache, browserContext, new DefaultFileBrowserIconProvider());
    }

    public BaseFileBrowserWidget(Path defaultDirectory,
                                 Path rootDirectory,
                                 @Nullable DirectoryCache cache,
                                 @Nullable String browserContext,
                                 FileBrowserIconProvider iconProvider)
    {
        super(Collections::emptyList, false);

        this.rootDirectory = rootDirectory;
        this.cache = cache;
        this.browserContext = browserContext != null ? browserContext : "";
        this.currentDirectory = cache != null ? cache.getCurrentDirectoryForContext(this.browserContext) : null;
        this.allowKeyboardNavigation = true;
        this.rememberScrollPosition = MaLiLibConfigs.Generic.REMEMBER_FILE_BROWSER_SCROLL_POSITIONS.getBooleanValue();
        this.showHiddenFiles = MaLiLibConfigs.Generic.FILE_BROWSER_SHOW_HIDDEN_FILES.getBooleanValue();
        this.entryWidgetFixedHeight = 14;
        this.scrollPositions.defaultReturnValue(0);

        if (this.currentDirectory == null)
        {
            this.currentDirectory = defaultDirectory;
        }

        this.navigationWidget = new DirectoryNavigationWidget(100, 14, this.currentDirectory,
                                                              rootDirectory, this, iconProvider,
                                                              this::onSearchBarTextChanged, this::refreshFilteredEntries,
                                                              this::getRootDirectoryDisplayName);
        this.searchBarWidget = this.navigationWidget;
        this.searchBarWidget.getMargin().setTop(2);
        this.defaultHeaderWidgetFactory = this::createFileListHeaderWidget;

        this.setDataListEntryWidgetFactory((data, constructData) ->
                                    new DirectoryEntryWidget(data, constructData, this, iconProvider));
        this.setWidgetInitializer(new DirectoryEntryWidget.WidgetInitializer());

        this.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, 0xC0000000);
        this.getBorderRenderer().getNormalSettings().setBorderWidthAndColor(1, 0xFF999999);
        this.listPosition.setRight(3);
        this.listPosition.setBottom(1);

        this.setAllowSelection(true);
        this.setShouldSortList(true);

        this.defaultListSortComparator = Comparator.naturalOrder();
        this.defaultSortColumn = DirectoryEntryWidget.NAME_COLUMN;
        this.setColumnSupplier(this::createFileBrowserColumns);
        this.updateActiveColumns();
    }

    public BaseFileBrowserWidget setAllowFileOperations(boolean allowFileOperations)
    {
        this.allowFileOperations = allowFileOperations;
        this.getEntrySelectionHandler().setAllowMultiSelection(allowFileOperations);
        this.getEntrySelectionHandler().setModifierKeyMultiSelection(allowFileOperations);
        return this;
    }

    public BaseFileBrowserWidget setFileFilter(Predicate<Path> filter)
    {
        this.fileFilter = filter;
        return this;
    }

    public BaseFileBrowserWidget setDirectoryFilter(Predicate<Path> directoryFilter)
    {
        this.directoryFilter = directoryFilter;
        return this;
    }

    public BaseFileBrowserWidget setRootDirectoryDisplayName(@Nullable String rootDirectoryDisplayName)
    {
        this.rootDirectoryDisplayName = rootDirectoryDisplayName;
        return this;
    }

    public BaseFileBrowserWidget setRememberScrollPosition(boolean rememberScrollPosition)
    {
        this.rememberScrollPosition = rememberScrollPosition;
        return this;
    }

    public boolean getShowFileSize()
    {
        return this.showFileSize;
    }

    public boolean getShowFileModificationTime()
    {
        return this.showFileModificationTime;
    }

    public void setShowFileSize(boolean showFileSize)
    {
        this.showFileSize = showFileSize;
        this.hasDataColumns = this.showFileSize || this.showFileModificationTime;
        this.updateActiveColumnsAndRefresh();
    }

    public void setShowFileModificationTime(boolean showFileModificationTime)
    {
        this.showFileModificationTime = showFileModificationTime;
        this.hasDataColumns = this.showFileSize || this.showFileModificationTime;
        this.updateActiveColumnsAndRefresh();
    }

    public void toggleShowHiddenFiles()
    {
        this.showHiddenFiles = ! this.showHiddenFiles;
        this.directoryContentsCache.clear();
        this.refreshEntries();
    }

    public void toggleShowFileSize()
    {
        this.setShowFileSize(! this.showFileSize);
    }

    public void toggleShowModificationTime()
    {
        this.setShowFileModificationTime(! this.showFileModificationTime);
    }

    protected List<DataColumn<DirectoryEntry>> createFileBrowserColumns()
    {
        ArrayList<DataColumn<DirectoryEntry>> list = new ArrayList<>();
        list.add(DirectoryEntryWidget.NAME_COLUMN);

        if (this.showFileSize)
        {
            list.add(DirectoryEntryWidget.SIZE_COLUMN);
        }

        if (this.showFileModificationTime)
        {
            list.add(DirectoryEntryWidget.TIME_COLUMN);
        }

        return list;
    }

    @Nullable
    protected String getRootDirectoryDisplayName()
    {
        return this.rootDirectoryDisplayName;
    }

    protected void updateDirectoryNavigationWidget()
    {
        this.navigationWidget.setCurrentDirectory(this.currentDirectory);
    }

    @Override
    public ArrayList<DirectoryEntry> getFilteredDataList()
    {
        return this.filteredDataList;
    }

    @Override
    protected void fetchCurrentEntries()
    {
        this.directoryContentsCache.clear();
    }

    @Override
    protected void reAddFilteredEntries()
    {
        this.filteredDataList.clear();

        Path dir = this.currentDirectory;

        if (Files.isDirectory(dir) && Files.isReadable(dir))
        {
            if (this.hasFilter())
            {
                this.addFilteredContents(dir);
            }
            else
            {
                this.addNonFilteredContents(dir);
            }
        }
    }

    protected void addNonFilteredContents(Path dir)
    {
        List<DirectoryEntry> list = new ArrayList<>();

        // Show directories at the top
        this.addMatchingEntriesToList(dir, list, this.getDirectoryFilter(), Collections.emptyList(), null);
        list.sort(this.activeListSortComparator);
        this.filteredDataList.addAll(list);
        list.clear();

        this.addMatchingEntriesToList(dir, list, this.getFileFilter(), Collections.emptyList(), null);
        this.sortEntryList(list);
        this.filteredDataList.addAll(list);
    }

    protected void addFilteredContents(Path dir)
    {
        String filterText = this.getSearchBarWidget().getFilter().toLowerCase();
        List<String> searchTerms = Arrays.asList(filterText.split("\\|"));
        List<DirectoryEntry> list = new ArrayList<>();
        this.addFilteredContents(dir, searchTerms, list, null);
        this.filteredDataList.addAll(list);
    }

    protected void addFilteredContents(Path dir,
                                       List<String> searchTerms,
                                       List<DirectoryEntry> listOut,
                                       @Nullable String prefix)
    {
        List<DirectoryEntry> list = new ArrayList<>();
        this.addMatchingEntriesToList(dir, list, this.getDirectoryFilter(), searchTerms, prefix);
        list.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        listOut.addAll(list);
        list.clear();

        for (Path subDir : this.getContents(dir, FileUtils.DIRECTORY_FILTER))
        {
            String pre;

            if (prefix != null)
            {
                pre = prefix + subDir.getFileName().toString() + "/";
            }
            else
            {
                pre = subDir.getFileName().toString() + "/";
            }

            this.addFilteredContents(subDir, searchTerms, list, pre);
            this.sortEntryList(list);
            listOut.addAll(list);
            list.clear();
        }

        this.addMatchingEntriesToList(dir, list, this.getFileFilter(), searchTerms, prefix);
        this.sortEntryList(list);
        listOut.addAll(list);
    }

    protected void addMatchingEntriesToList(final Path dir, List<DirectoryEntry> outputList, final Predicate<Path> filter,
                                            List<String> searchTerms, @Nullable String displayNamePrefix)
    {
        for (Path file : this.getContents(dir, filter))
        {
            String fileName = file.getFileName().toString();
            String entryString = FileNameUtils.getFileNameWithoutExtension(fileName.toLowerCase(Locale.ROOT));

            if (searchTerms.isEmpty() || this.fileNameMatchesFilter(entryString, searchTerms))
            {
                DirectoryEntryType type = DirectoryEntryType.fromFile(file);
                outputList.add(new DirectoryEntry(type, dir, fileName, displayNamePrefix));
            }
        }
    }

    protected boolean fileNameMatchesFilter(String entryString, List<String> searchTerms)
    {
        for (String searchTerm : searchTerms)
        {
            if (entryString.contains(searchTerm))
            {
                return true;
            }
        }

        return false;
    }

    protected List<Path> getContents(final Path dir, Predicate<Path> filter)
    {
        final Predicate<Path> finalFilter = this.getFileFilterObeyingHiddenFiles(filter);
        Pair<Path, Predicate<Path>> cacheKey = Pair.of(dir, finalFilter); // FIXME this won't work now...
        return this.directoryContentsCache.computeIfAbsent(cacheKey, (k) -> FileUtils.getDirectoryContents(dir, finalFilter, false));
    }

    protected Predicate<Path> getFileFilterObeyingHiddenFiles(Predicate<Path> original)
    {
        if (this.showHiddenFiles == false)
        {
            return f -> f.getFileName().toString().startsWith(".") == false && original.test(f);
        }

        return original;
    }

    protected Path getRootDirectory()
    {
        return this.rootDirectory;
    }

    protected Predicate<Path> getDirectoryFilter()
    {
        return this.directoryFilter;
    }

    protected Predicate<Path> getFileFilter()
    {
        return this.fileFilter;
    }

    @Override
    public int getHeightForListEntryWidgetCreation(int listIndex)
    {
        if (listIndex >= 0 && listIndex < this.filteredDataList.size())
        {
            DirectoryEntry entry = this.filteredDataList.get(listIndex);
            return entry.getType() == DirectoryEntryType.DIRECTORY ? 14 : this.entryWidgetFixedHeight;
        }

        return this.entryWidgetFixedHeight;
    }

    protected boolean currentDirectoryIsRoot()
    {
        return this.currentDirectory.equals(this.getRootDirectory());
    }

    protected void storeKeyboardNavigationPosition(Path dir)
    {
        int index = this.getKeyboardNavigationIndex();

        if (index != -1 && this.shouldStoreKeyboardNavigationPosition)
        {
            this.keyboardNavigationPositions.put(dir, index);
        }
    }

    protected void restoreKeyboardNavigationPosition(Path dir)
    {
        if (this.shouldStoreKeyboardNavigationPosition &&
            this.keyboardNavigationPositions.containsKey(dir))
        {
            int index = this.keyboardNavigationPositions.computeIfAbsent(dir, (d) -> 0);
            this.setKeyboardNavigationIndex(index);
        }
        else if (this.getKeyboardNavigationIndex() > 0)
        {
            this.setKeyboardNavigationIndex(0);
        }
    }

    protected void storeCurrentScrollPosition(Path dir)
    {
        if (this.rememberScrollPosition)
        {
            this.scrollPositions.put(dir, this.scrollBar.getValue());
        }
    }

    protected void restoreScrollBarPosition(Path dir)
    {
        if (this.rememberScrollPosition)
        {
            int position = this.scrollPositions.getInt(dir);
            this.setRequestedScrollBarPosition(position);
        }
    }

    protected DataListHeaderWidget<DirectoryEntry> createFileListHeaderWidget(DataListWidget<DirectoryEntry> listWidget)
    {
        ColumnizedDataListHeaderWidget<DirectoryEntry> widget =
                new ColumnizedDataListHeaderWidget<>(this.entryWidgetWidth, 14, this, this.columns);
        widget.getMargin().setAll(2, 0, 0, 1);
        return widget;
    }

    protected void openSettingsContextMenu(int mouseX, int mouseY)
    {
        MenuWidget menuWidget = new MenuWidget(mouseX + 4, mouseY, 10, 10);
        menuWidget.setMenuEntries(this.getSettingsMenuEntries());
        menuWidget.setMenuCloseHook(this::closeCurrentContextMenu);
        this.openContextMenu(menuWidget);
    }

    public void openContextMenuForEntry(int mouseX, int mouseY, int listIndex)
    {
        if (this.allowFileOperations == false)
        {
            return;
        }

        DataListEntrySelectionHandler<DirectoryEntry> handler = this.getEntrySelectionHandler();

        // If right-clicking on a non-selected entry, clear the selection and select just that one entry
        // (This is to mimic the behavior of the windblows explorer context menu)
        if (listIndex >= 0 && handler.isEntrySelected(listIndex) == false)
        {
            handler.clearSelection();
            handler.setSelectedEntry(listIndex);
        }

        List<MenuEntryWidget> entries = new ArrayList<>(listIndex >= 0 ?
                                                        this.getFileOperationMenuEntriesForFile() :
                                                        this.getFileOperationMenuEntriesForNonFile());
        entries.addAll(this.getSettingsMenuEntries());

        MenuWidget menuWidget = new MenuWidget(mouseX + 4, mouseY, 10, 10);
        menuWidget.setMenuCloseHook(this::closeCurrentContextMenu);
        menuWidget.setMenuEntries(entries);

        this.openContextMenu(menuWidget);
    }

    protected List<MenuEntryWidget> getFileOperationMenuEntriesForNonFile()
    {
        StyledTextLine textPaste  = StyledTextLine.translate("malilib.label.file_browser.context_menu.paste");
        boolean hasFiles = this.operatedOnFiles.isEmpty() == false;
        return ImmutableList.of(new MenuEntryWidget(textPaste, this::pasteFiles, hasFiles));
    }

    protected List<MenuEntryWidget> getFileOperationMenuEntriesForFile()
    {
        StyledTextLine textCopy   = StyledTextLine.translate("malilib.label.file_browser.context_menu.copy");
        StyledTextLine textCut    = StyledTextLine.translate("malilib.label.file_browser.context_menu.cut");
        StyledTextLine textDelete = StyledTextLine.translate("malilib.label.file_browser.context_menu.delete");
        StyledTextLine textPaste  = StyledTextLine.translate("malilib.label.file_browser.context_menu.paste");
        StyledTextLine textRename = StyledTextLine.translate("malilib.label.file_browser.context_menu.rename");
        boolean hasFiles = this.operatedOnFiles.isEmpty() == false;

        return ImmutableList.of(new MenuEntryWidget(textCopy,   this::copyFiles),
                                new MenuEntryWidget(textCut,    this::cutFiles),
                                new MenuEntryWidget(textPaste,  this::pasteFiles, hasFiles),
                                new MenuEntryWidget(textDelete, this::deleteFiles),
                                new MenuEntryWidget(textRename, this::renameFiles));
    }

    protected List<MenuEntryWidget> getSettingsMenuEntries()
    {
        String sizeKey = this.showFileSize ? "malilib.label.file_browser.context_menu.hide_file_size" : "malilib.label.file_browser.context_menu.show_file_size";
        String mTimeKey = this.showFileModificationTime ? "malilib.label.file_browser.context_menu.hide_file_mtime" : "malilib.label.file_browser.context_menu.show_file_mtime";
        String hiddenKey = this.showHiddenFiles ? "malilib.label.file_browser.context_menu.dont_show_hidden_files" : "malilib.label.file_browser.context_menu.show_hidden_files";
        StyledTextLine textShowSize = StyledTextLine.translate(sizeKey);
        StyledTextLine textShowDate = StyledTextLine.translate(mTimeKey);
        StyledTextLine textShowHidden = StyledTextLine.translate(hiddenKey);

        return ImmutableList.of(new MenuEntryWidget(textShowSize, this::toggleShowFileSize),
                                new MenuEntryWidget(textShowDate, this::toggleShowModificationTime),
                                new MenuEntryWidget(textShowHidden, this::toggleShowHiddenFiles));
    }

    protected void copyFiles()
    {
        this.storeSelectionAsOperatedOnFiles();
        this.pendingOperationIsCut = false;
    }

    protected void cutFiles()
    {
        this.storeSelectionAsOperatedOnFiles();
        this.pendingOperationIsCut = true;
    }

    protected void pasteFiles()
    {
        List<String> messages = new ArrayList<>();

        if (this.pendingOperationIsCut)
        {
            FileUtils.moveFilesToDirectory(this.operatedOnFiles, this.getCurrentDirectory(), messages::add);
        }
        else
        {
            FileUtils.copyFilesToDirectory(this.operatedOnFiles, this.getCurrentDirectory(), messages::add);
        }

        this.endFileOperation(messages);
    }

    protected void deleteFiles()
    {
        this.storeSelectionAsOperatedOnFiles();

        ConfirmActionScreen screen = new ConfirmActionScreen(320, "malilib.title.screen.confirm_file_deletion",
                                                             this::executeDeleteFiles,
                                                             "malilib.label.confirm.file_deletion",
                                                             this.operatedOnFiles.size());
        screen.setParent(GuiUtils.getCurrentScreen());
        BaseScreen.openPopupScreen(screen);
    }

    protected boolean executeDeleteFiles()
    {
        List<String> messages = new ArrayList<>();
        FileUtils.deleteFiles(this.operatedOnFiles, messages::add);
        this.endFileOperation(messages);
        return true;
    }

    protected void renameFiles()
    {
        this.storeSelectionAsOperatedOnFiles();

        List<Path> files = new ArrayList<>(this.operatedOnFiles);
        files.sort(Comparator.comparing(Path::getFileName));

        DataIteratingTask<Path> task = new DataIteratingTask<>(files, this::renameFile, this::endFileOperation);
        task.advance();
    }

    protected void renameFile(Path file, DataIteratingTask<Path> task)
    {
        String originalFileName = file.getFileName().toString();
        String name = FileNameUtils.getFileNameWithoutExtension(originalFileName);

        boolean isDir = Files.isDirectory(file);
        String titleKey = isDir ? "malilib.title.screen.rename_directory" :
                                  "malilib.title.screen.rename_file";
        String infoKey = isDir ? "malilib.label.file_browser.rename.info.directory" :
                                 "malilib.label.file_browser.rename.info.file";
        TextInputScreen screen = new TextInputScreen(titleKey, name, (n) -> this.renameFile(file, n));
        screen.setInfoText(StyledText.translate(infoKey, originalFileName));
        screen.setLabelText(StyledText.translate("malilib.label.file_browser.rename.new_name"));
        screen.setConfirmListener(task::advance);
        screen.setCancelListener(task::cancel);
        screen.setParent(GuiUtils.getCurrentScreen());

        BaseScreen.openPopupScreen(screen);
    }

    protected boolean renameFile(Path file, String newName)
    {
        String originalFileName = file.getFileName().toString();

        // Same name, NO-OP
        if (newName.equals(FileNameUtils.getFileNameWithoutExtension(originalFileName)))
        {
            return true;
        }

        Path dir = file.getParent();
        String extension = FileNameUtils.getFileNameExtension(originalFileName);

        if (extension.length() > 0)
        {
            extension = "." + extension;
        }

        return FileUtils.move(file, dir.resolve(newName + extension), false, MessageDispatcher.error()::send);
    }

    protected void endFileOperation(List<String> messages)
    {
        this.endFileOperation();

        if (messages.isEmpty() == false)
        {
            for (String msg : messages)
            {
                MessageDispatcher.error().send(msg);
            }
        }
    }

    protected void endFileOperation()
    {
        this.operatedOnFiles.clear();
        this.directoryContentsCache.clear();
        this.getEntrySelectionHandler().clearSelection();
        this.pendingOperationIsCut = false;
        this.refreshEntries();
    }

    protected void storeSelectionAsOperatedOnFiles()
    {
        this.operatedOnFiles.clear();

        DataListEntrySelectionHandler<DirectoryEntry> handler = this.getEntrySelectionHandler();

        for (DirectoryEntry entry : handler.getSelectedEntries())
        {
            this.operatedOnFiles.add(entry.getFullPath());
        }
    }

    @Override
    public Path getCurrentDirectory()
    {
        return this.currentDirectory;
    }

    @Override
    public void switchToDirectory(Path dir)
    {
        boolean hadSelection = this.getLastSelectedEntry() != null;

        this.storeKeyboardNavigationPosition(this.currentDirectory);
        this.storeCurrentScrollPosition(this.currentDirectory);
        this.clearSelection();

        this.currentDirectory = dir.toAbsolutePath();

        if (this.cache != null)
        {
            this.cache.setCurrentDirectoryForContext(this.browserContext, dir);
        }

        this.resetScrollBarPositionWithoutNotify();
        this.restoreScrollBarPosition(dir);
        this.directoryContentsCache.clear();

        this.refreshEntries();
        this.updateDirectoryNavigationWidget();
        // The index needs to be restored after the entries have been refreshed
        this.restoreKeyboardNavigationPosition(this.currentDirectory);

        if (hadSelection)
        {
            this.notifySelectionListener();
        }
    }

    @Override
    public void switchToRootDirectory()
    {
        this.switchToDirectory(this.getRootDirectory());
    }

    @Override
    public void switchToParentDirectory()
    {
        Path parent = this.currentDirectory.getParent();

        if (this.currentDirectoryIsRoot() == false && parent != null &&
            this.currentDirectory.toAbsolutePath().startsWith(this.getRootDirectory().toAbsolutePath()))
        {
            this.switchToDirectory(parent);
        }
        else
        {
            this.switchToRootDirectory();
        }
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 1)
        {
            if (this.isMouseOverListArea(mouseX, mouseY) == false)
            {
                this.openSettingsContextMenu(mouseX, mouseY);
            }
            else
            {
                this.openContextMenuForEntry(mouseX, mouseY, -1);
            }

            return true;
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers)
    {
        if (super.onKeyTyped(keyCode, scanCode, modifiers))
        {
            return true;
        }

        if ((keyCode == Keys.KEY_BACKSPACE || keyCode == Keys.KEY_LEFT) && this.currentDirectoryIsRoot() == false)
        {
            this.switchToParentDirectory();
            return true;
        }
        else if (keyCode == Keys.KEY_RIGHT || keyCode == Keys.KEY_ENTER)
        {
            DirectoryEntry entry = this.getKeyboardNavigationEntry();

            if (entry != null && entry.getType() == DirectoryEntryType.DIRECTORY)
            {
                this.switchToDirectory(entry.getDirectory().resolve(entry.getName()));
                return true;
            }
        }
        else if (keyCode == Keys.KEY_N && BaseScreen.isCtrlDown() && BaseScreen.isShiftDown())
        {
            DirectoryCreator creator = new DirectoryCreator(this.getCurrentDirectory(), this);
            TextInputScreen screen = new TextInputScreen("malilib.title.screen.create_directory", "", creator);
            screen.setParent(GuiUtils.getCurrentScreen());
            BaseScreen.openPopupScreen(screen);
            return true;
        }

        return false;
    }

    public static class DirectoryEntry implements Comparable<DirectoryEntry>
    {
        protected final DirectoryEntryType type;
        protected final Path dir;
        protected final String name;
        @Nullable protected final String displayNamePrefix;

        public DirectoryEntry(DirectoryEntryType type, Path dir, String name, @Nullable String displayNamePrefix)
        {
            this.type = type;
            this.dir = dir;
            this.name = name;
            this.displayNamePrefix = displayNamePrefix;
        }

        public DirectoryEntryType getType()
        {
            return this.type;
        }

        public Path getDirectory()
        {
            return this.dir;
        }

        public String getName()
        {
            return this.name;
        }

        @Nullable
        public String getDisplayNamePrefix()
        {
            return this.displayNamePrefix;
        }

        public String getDisplayName()
        {
            return this.displayNamePrefix != null ? this.displayNamePrefix + this.name : this.name;
        }

        public Path getFullPath()
        {
            return this.dir.resolve(this.name);
        }

        @Override
        public int compareTo(DirectoryEntry other)
        {
            return this.name.toLowerCase(Locale.US).compareTo(other.getName().toLowerCase(Locale.US));
        }
    }

    public enum DirectoryEntryType
    {
        INVALID,
        DIRECTORY,
        FILE;

        public static DirectoryEntryType fromFile(Path file)
        {
            if (Files.exists(file) == false)
            {
                return INVALID;
            }

            if (Files.isDirectory(file))
            {
                return DIRECTORY;
            }
            else if (Files.isRegularFile(file))
            {
                return FILE;
            }

            return INVALID;
        }
    }
}
