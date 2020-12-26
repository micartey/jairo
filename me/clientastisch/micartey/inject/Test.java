package me.clientastisch.micartey.inject;

import me.clientastisch.micartey.transformer.annotations.Hook;
import me.clientastisch.micartey.transformer.annotations.Overwrite;

@Hook("net.minecraft.server.v1_8_R3.PacketHandshakingInSetProtocol")
public interface Test {

    @Overwrite(
            value = Overwrite.Type.REPLACE,
            body = {
                    "System.out.println(\"hallo\");"
            }
    )
    void a();

}
