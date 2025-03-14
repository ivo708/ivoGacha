package com.example;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.inventory.SimpleInventory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootUtil {

    // Clase interna para representar cada entrada de la loot table
    public static class LootEntry {
        public String Tipo;
        public String namespace;
        public String displayName;
        public String id;
        public int weight;
        public int cantidad;
        public String custom_model_data;
        public String custom_data;
        public PokemonProperties pokemon_properties;

        public static class PokemonProperties {
            public String especie;
            public String form;
            public boolean shiny;
            public String ability;
            public int HP_iv;
            public int ATTACK_iv;
            public int DEFENCE_iv;
            public int SPECIAL_ATTACK_iv;
            public int SPECIAL_DEFENCE_iv;
            public int SPEED_iv;
            public List<String> moves;
        }
    }
    public static ItemStack createItemStack(String namespace, String id, int amount, String customModelData, String customData,String displayName) {
        Identifier itemId = Identifier.of(namespace, id);
        Item item = Registries.ITEM.get(itemId);
    
        if (item == null || item == ItemStack.EMPTY.getItem()) {
            throw new IllegalArgumentException("El item con la ID " + itemId + " no existe.");
        }


        ItemStack stack = new ItemStack(item, amount);
        if (customModelData != null && !customModelData.equals("null")) {
            try {
                int cmd = Integer.parseInt(customModelData);
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(cmd));
            } catch (NumberFormatException e) {
                System.err.println("Error al parsear custom_model_data: " + customModelData);
            }
        }
        if (displayName != null && !displayName.isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.of(displayName));

        }
        if (customData != null && !customData.equals("null")) {
            try {
                String[] parts = customData.split(":");
                if (parts.length == 2) {
                    NbtCompound cd = new NbtCompound();
                    cd.putString(parts[0], parts[1]);
                    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(cd));
                }
            } catch (Exception e) {
                System.err.println("Error al parsear custom_data: " + customData);
            }
        }
        return stack;
    }
    
    /**
     * Método auxiliar que crea el ItemStack basado en la loot entry y le añade toda la información de la misma en custom data.
     * - Para Tipo "Item": se crea el ItemStack con createItemStack y se asigna un NBT que deja pokemon_properties vacío.
     * - Para Tipo "Pokemon": se genera el ItemStack a partir del Pokémon y se guarda en el NBT la información de pokemon_properties.
     */
    public static ItemStack buildItemStack(LootEntry selected) {
        ItemStack stack = null;
        if ("Item".equalsIgnoreCase(selected.Tipo)) {
            stack = createItemStack(selected.namespace, selected.id, selected.cantidad, selected.custom_model_data, selected.custom_data,selected.displayName);
        } else if ("Pokemon".equalsIgnoreCase(selected.Tipo)) {
            Pokemon pokemon = PokemonSpecies.INSTANCE.getByName(selected.pokemon_properties.especie).create(1);
            pokemon.setShiny(selected.pokemon_properties.shiny);
            stack = PokemonItem.from(pokemon);
        }
        NbtCompound lootTag = new NbtCompound();
        lootTag.putString("Tipo", selected.Tipo);
        lootTag.putString("namespace", selected.namespace);
        lootTag.putString("displayName", selected.displayName != null ? selected.displayName : "");
        lootTag.putString("id", selected.id);
        lootTag.putInt("weight", selected.weight);
        lootTag.putInt("cantidad", selected.cantidad);
        lootTag.putString("custom_model_data", selected.custom_model_data != null ? selected.custom_model_data : "");
        lootTag.putString("custom_data", selected.custom_data != null ? selected.custom_data : "");

        
        if ("Pokemon".equalsIgnoreCase(selected.Tipo) && selected.pokemon_properties != null) {
            NbtCompound pokemonProps = new NbtCompound();
            if (selected.pokemon_properties.especie != null) {
                pokemonProps.putString("especie", selected.pokemon_properties.especie);
            }
            if (selected.pokemon_properties.form != null) {
                pokemonProps.putString("form", selected.pokemon_properties.form);
            }
            pokemonProps.putBoolean("shiny", selected.pokemon_properties.shiny);
            if (selected.pokemon_properties.ability != null) {
                pokemonProps.putString("ability", selected.pokemon_properties.ability);
            }
            pokemonProps.putInt("HP_iv", selected.pokemon_properties.HP_iv);
            pokemonProps.putInt("ATTACK_iv", selected.pokemon_properties.ATTACK_iv);
            pokemonProps.putInt("DEFENCE_iv", selected.pokemon_properties.DEFENCE_iv);
            pokemonProps.putInt("SPECIAL_ATTACK_iv", selected.pokemon_properties.SPECIAL_ATTACK_iv);
            pokemonProps.putInt("SPECIAL_DEFENCE_iv", selected.pokemon_properties.SPECIAL_DEFENCE_iv);
            pokemonProps.putInt("SPEED_iv", selected.pokemon_properties.SPEED_iv);
            NbtList movesList = new NbtList();
            if (selected.pokemon_properties.moves != null) {
                for (String move : selected.pokemon_properties.moves) {
                    movesList.add(net.minecraft.nbt.NbtString.of(move));
                }
            }
            pokemonProps.put("moves", movesList);
            lootTag.put("pokemon_properties", pokemonProps);
        } else {
            // Para Tipo "Item", dejamos el tag "pokemon_properties" vacío (equivalente a nulo)
            lootTag.put("pokemon_properties", new NbtCompound());
        }
        
        // Se utiliza custom data para almacenar el NBT de la loot entry
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(lootTag));
        return stack;
    }
    
    /**
     * Lee la loot table, selecciona aleatoriamente una entrada basada en el weight y asigna el ItemStack generado al inventario.
     */
    public static SimpleInventory setLootItem(SimpleInventory inventory, String lootTable, int row) {
        File file = new File("config/ivogacha/pools", lootTable + ".json");
        if (!file.exists()) {
            System.err.println("Archivo de loot table no encontrado: " + file.getAbsolutePath());
            return inventory;
        }
        
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            Type lootEntryListType = new TypeToken<List<LootEntry>>(){}.getType();
            List<LootEntry> lootEntries = gson.fromJson(json, lootEntryListType);
            
            // Seleccionar una entrada aleatoria basada en el weight
            int totalWeight = lootEntries.stream().mapToInt(entry -> entry.weight).sum();
            int random = new Random().nextInt(totalWeight);
            LootEntry selected = null;
            for (LootEntry entry : lootEntries) {
                random -= entry.weight;
                if (random < 0) {
                    selected = entry;
                    break;
                }
            }
            
            if (selected != null) {
                ItemStack stack = buildItemStack(selected);
                
                // Desplazar los ítems en el inventario y colocar el nuevo stack en la posición correspondiente
                for (int i = (9 * row) - 1; i > (9 * (row - 1)); i--) {	                    
                    inventory.setStack(i, inventory.getStack(i - 1));
                }
                inventory.setStack((9 * (row - 1)), stack);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inventory;
    }
    
    public static SimpleInventory init(SimpleInventory inventory, String lootTable, boolean isMulti) {
        ItemStack glassPane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        if (isMulti) {
            inventory.setStack(20, glassPane);
            inventory.setStack(21, glassPane);
            inventory.setStack(22, glassPane);
            inventory.setStack(23, glassPane);
            inventory.setStack(24, glassPane);
            inventory.setStack(29, glassPane);
            inventory.setStack(30, glassPane);
            inventory.setStack(31, glassPane);
            inventory.setStack(32, glassPane);
            inventory.setStack(33, glassPane);
        } else {
            inventory.setStack(4, glassPane);
            inventory.setStack(22, glassPane);
        }
        
        for (int i = 0; i < 10; i++) {
            inventory = setLootItem(inventory, lootTable, 2);
            if (isMulti) {
                inventory = setLootItem(inventory, lootTable, 5);
            }
        }
        return inventory;
    }
    
    
    public static LootEntry getLootEntryFromSlot(SimpleInventory inventory, int slot) {
        ItemStack stack = inventory.getStack(slot);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        
        // Se asume que se utilizó custom data para almacenar la loot entry.
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent == null) {
            return null;
        }
        
        NbtCompound lootTag = nbtComponent.getNbt();
        if (lootTag == null || !lootTag.contains("Tipo")) {
            return null;
        }
        
        LootEntry lootEntry = new LootEntry();
        lootEntry.Tipo = lootTag.getString("Tipo");
        lootEntry.namespace = lootTag.getString("namespace");
        lootEntry.displayName = lootTag.getString("displayName");
        lootEntry.id = lootTag.getString("id");
        lootEntry.weight = lootTag.getInt("weight");
        lootEntry.cantidad = lootTag.getInt("cantidad");
        lootEntry.custom_model_data = lootTag.getString("custom_model_data");
        lootEntry.custom_data = lootTag.getString("custom_data");
        
        if (lootTag.contains("pokemon_properties")) {
            NbtCompound props = lootTag.getCompound("pokemon_properties");
            // Si el compound no está vacío, se reconstruyen las propiedades del Pokémon
            if (!props.isEmpty()) {
                LootEntry.PokemonProperties pokemonProps = new LootEntry.PokemonProperties();
                pokemonProps.especie = props.getString("especie");
                pokemonProps.form = props.getString("form");
                pokemonProps.shiny = props.getBoolean("shiny");
                pokemonProps.ability = props.getString("ability");
                pokemonProps.HP_iv = props.getInt("HP_iv");
                pokemonProps.ATTACK_iv = props.getInt("ATTACK_iv");
                pokemonProps.DEFENCE_iv = props.getInt("DEFENCE_iv");
                pokemonProps.SPECIAL_ATTACK_iv = props.getInt("SPECIAL_ATTACK_iv");
                pokemonProps.SPECIAL_DEFENCE_iv = props.getInt("SPECIAL_DEFENCE_iv");
                pokemonProps.SPEED_iv = props.getInt("SPEED_iv");
                
                NbtList movesList = props.getList("moves", 8); // 8 corresponde a los strings
                List<String> moves = new ArrayList<>();
                for (int i = 0; i < movesList.size(); i++) {
                    moves.add(movesList.getString(i));
                }
                pokemonProps.moves = moves;
                lootEntry.pokemon_properties = pokemonProps;
            } else {
                lootEntry.pokemon_properties = null;
            }
        } else {
            lootEntry.pokemon_properties = null;
        }
        
        return lootEntry;
    }
    
    
    
    
    
}
