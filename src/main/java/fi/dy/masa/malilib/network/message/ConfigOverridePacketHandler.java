package fi.dy.masa.malilib.network.message;

import java.util.List;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import fi.dy.masa.malilib.config.util.ConfigOverrideUtils;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class ConfigOverridePacketHandler extends BasePacketHandler
{
    public static final String CHANNEL_NAME = "malilib:cfgovrrd";
    public static final List<ResourceLocation> CHANNELS = ImmutableList.of(new ResourceLocation(CHANNEL_NAME));

    private static final ConfigOverridePacketHandler INSTANCE = new ConfigOverridePacketHandler();

    @Override
    public List<ResourceLocation> getChannels()
    {
        return CHANNELS;
    }

    @Override
    public void onPacketReceived(PacketBuffer buf)
    {
        try
        {
            boolean resetFirst = buf.readBoolean();
            String str = buf.readString(256 * 1024);
            JsonElement el = JsonUtils.parseJsonFromString(str);

            if (el != null && el.isJsonObject())
            {
                if (resetFirst)
                {
                    ConfigOverrideUtils.resetConfigOverrides();
                }

                ConfigOverrideUtils.applyConfigOverrides(el.getAsJsonObject());

                return;
            }
        }
        catch (Exception ignore) {}

        MessageDispatcher.error().console().translate("malilib.message.error.invalid_config_override_packet");
    }

    public static void updateRegistration(boolean enabled)
    {
        if (enabled)
        {
            Registry.CLIENT_PACKET_CHANNEL_HANDLER.registerClientChannelHandler(INSTANCE);
        }
        else
        {
            Registry.CLIENT_PACKET_CHANNEL_HANDLER.unregisterClientChannelHandler(INSTANCE);
        }
    }
}
