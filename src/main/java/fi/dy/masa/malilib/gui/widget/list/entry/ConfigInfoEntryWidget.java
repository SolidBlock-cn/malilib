package fi.dy.masa.malilib.gui.widget.list.entry;

import fi.dy.masa.malilib.config.option.ConfigInfo;
import fi.dy.masa.malilib.render.text.StyledTextLine;
import fi.dy.masa.malilib.util.data.ConfigOnTab;

public class ConfigInfoEntryWidget extends BaseDataListEntryWidget<ConfigOnTab>
{
    public ConfigInfoEntryWidget(ConfigOnTab data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        ConfigInfo config = data.getConfig();
        this.translateAndAddHoverString("malilib.hover.config.info_entry_widget",
                                        config.getDisplayName(), config.getName(),
                                        config.getClass().getSimpleName());

        this.setText(StyledTextLine.of(config.getDisplayName()));

        int bgColor = this.isOdd ? 0x30FFFFFF : 0x20FFFFFF;
        this.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, bgColor);
    }
}
