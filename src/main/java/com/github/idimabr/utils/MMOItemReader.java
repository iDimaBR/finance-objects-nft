package com.github.idimabr.utils;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import org.bukkit.Bukkit;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Base64;

public class MMOItemReader {

    public static VolatileMMOItem readUnidentifiedItem(NBTItem item) {
        // Pega o valor serializado do NBT
        String serialized = item.getString("MMOITEMS_UNIDENTIFIED_ITEM");
        if (serialized == null || serialized.isEmpty()) return null;

        try {
            byte[] bytes = Base64.getDecoder().decode(serialized);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object obj = ois.readObject(); // Desserializa o objeto

            if (obj instanceof VolatileMMOItem vItem) {
                return vItem; // Retorna o item MMOItems desserializado
            } else {
                Bukkit.getLogger().warning("O objeto desserializado não é VolatileMMOItem!");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }
}
