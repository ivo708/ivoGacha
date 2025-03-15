package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.example.LootUtil.LootEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Ivogacha implements ModInitializer {
	public static final String MOD_ID = "ivogacha";
    private static JsonObject config;
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier CHEST_OPEN_PACKET_ID = Identifier.of("ivogacha", "chest_open");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    @Override
    public void onInitialize() {
        loadConfig();
        // Registrar el comando
        ChestOpenPayload.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
            dispatcher.register(CommandManager.literal("ivogacha")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    openMenu(player);
                    return 1;
                })
                .then(CommandManager.literal("reload")
                    .executes(context -> {
                        loadConfig();
                        return 1;
                    })
                )
            );
        });
    }

    public static void openMenu(ServerPlayerEntity player) {
        int rows = 3;
        SimpleInventory inventory = new SimpleInventory(rows * 9) {
            @Override
            public boolean isValid(int slot, ItemStack stack) {
                return false;
            }
        };
        Style style = Style.EMPTY.withItalic(true).withBold(true);

        // 1. Rellenar cada slot del inventario con un panel de cristal aleatorio
        Random random = new Random();
        int inventorySize = inventory.size(); // Ajusta según tu implementación
        for (int slot = 0; slot < inventorySize; slot++) {
            double chance = random.nextDouble();
            ItemStack glassPane;
            if (chance < 0.44) {
                glassPane = new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE);
            } else if (chance < 0.88) {
                glassPane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
            } else {
                glassPane = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
            }
            inventory.setStack(slot, glassPane);
        }

        JsonObject configJson = config;
        List<Integer> slotsUsados = new ArrayList<>();
        if (configJson != null && configJson.has("pools")) {
            JsonArray poolsArray = configJson.getAsJsonArray("pools");
            for (JsonElement element : poolsArray) {
                if (element.isJsonObject()) {
                    JsonObject poolEntry = element.getAsJsonObject();
                    int slot = poolEntry.get("slot").getAsInt();
                    slotsUsados.add(slot);
                    String nombre = poolEntry.get("nombre").getAsString();
                    String colorNombre = poolEntry.get("colorNombre").getAsString();                    
                    String lore = poolEntry.get("lore").getAsString();                    
                    String colorLore = poolEntry.get("colorLore").getAsString();   
                    Style estiloNombre = style.withColor(TextColor.parse(colorNombre).getOrThrow());
                    Style estiloLore = style.withColor(TextColor.parse(colorLore).getOrThrow());
                    ItemStack chestItem = new ItemStack(Items.CHEST);
                    if(poolEntry.get("chestDisplayCustomModelData").getAsInt()>0) {
                    	chestItem=new ItemStack(Items.BARRIER);
                        chestItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(poolEntry.get("chestDisplayCustomModelData").getAsInt()));
                    }
                    chestItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal(nombre).setStyle(estiloNombre));
                    List<Text> loreList = new ArrayList<>();
                    loreList.add(Text.literal(lore).setStyle(estiloLore));
                    loreList.add(Text.literal("Click izquierdo - x1").setStyle(style.withColor(TextColor.parse("gray").getOrThrow())));
                    loreList.add(Text.literal("Click derecho - x10").setStyle(style.withColor(TextColor.parse("gray").getOrThrow())));
                    chestItem.set(DataComponentTypes.LORE, new LoreComponent(loreList));
                    inventory.setStack(slot, chestItem);
                }
            }
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, playerEntity) -> {
            return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, rows) {
                @Override
                public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
                    return true; // Permite que el jugador solo vea el inventario sin interacción
                }

                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
                    JsonArray poolsArray = config.getAsJsonArray("pools");
                    Stream<JsonElement> stream = StreamSupport.stream(poolsArray.spliterator(), false);
                    Optional<JsonObject> poolSeleccionada = stream
                        .filter(JsonElement::isJsonObject)
                        .map(JsonElement::getAsJsonObject)
                        .filter(poolEntry -> poolEntry.has("slot") && poolEntry.get("slot").getAsInt() == slotIndex)
                        .findFirst();
                    poolSeleccionada.ifPresent(pool -> {
                        ((ServerPlayerEntity) player).closeHandledScreen();
                        if (button == 0) {
                            useGacha((ServerPlayerEntity) player, pool, false);
                        } else if (button == 1) {
                            useGacha((ServerPlayerEntity) player, pool, true);
                        }
                    });
                }

            };
        }, Text.literal("GACHAPON").setStyle(style.withColor(TextColor.parse("white").getOrThrow())) ));
    }
    
    public static void useGacha(ServerPlayerEntity serverPlayer, JsonObject pool,boolean isMulti) {
        JsonObject coste = pool.getAsJsonObject("coste");
        String namespace = coste.get("namespace").getAsString();
        String id = coste.get("id").getAsString();
        int cantidad = coste.get("cantidad").getAsInt();
        if(isMulti) {
        	cantidad=cantidad*10;
        }
    	Identifier costeID = Identifier.of(namespace,id);
    	Item itemCoste = Registries.ITEM.get(costeID);
    	if(!playerHasItem(serverPlayer,itemCoste,cantidad)){
    	    Text mensaje = Text.literal("No posees los Items necesarios")
    	            .styled(style -> style
    	                .withColor(TextColor.fromFormatting(Formatting.RED))
    	                .withBold(true)
    	                .withItalic(true)
    	            );
    	    serverPlayer.sendMessage(mensaje);
    	    return;
    	}
    	playerClearItems(serverPlayer,itemCoste,cantidad);
        JsonObject coords = config.getAsJsonObject("coords");
        int x = coords.get("x").getAsInt();
        int y = coords.get("y").getAsInt();
        int z = coords.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);
        int CDCMD = pool.get("chestDisplayCustomModelData").getAsInt();    
        if(isMulti) {
        	CDCMD = pool.get("chestDisplayCustomModelDataMulti").getAsInt();  
        }
        ChestOpenPayload payload = new ChestOpenPayload(pos,CDCMD);

        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);

        ServerPlayNetworking.send(serverPlayer,new ChestOpenPayload(buf));
        scheduler.schedule(() -> {
            final SimpleInventory[] inventoryHolder = new SimpleInventory[1];            
            int[] rows= {3};
            if(isMulti) {
            	rows[0]=6;
            }
            inventoryHolder[0] = new SimpleInventory(rows[0] * 9) {
            	@Override
            	public boolean isValid(int slot, ItemStack stack) {
            		return false;
            	}
            };
            inventoryHolder[0] = fillDefaultInventory(inventoryHolder[0]);
            inventoryHolder[0]=LootUtil.init(inventoryHolder[0],pool.get("item_pool").getAsString(),isMulti);
            int ticks = 1;
            int total = ticks * 50;
            Style style = Style.EMPTY.withItalic(true).withBold(true);
            boolean[] isFinished= {false};
            if (!isMulti) {
            	final NamedScreenHandlerFactory[] factoryHolder = new NamedScreenHandlerFactory[1];
            	factoryHolder[0] = new SimpleNamedScreenHandlerFactory(
            			(syncId, playerInventory, playerEntity) -> {
            				return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventoryHolder[0], rows[0]) {
            					@Override
            					public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
            						return true;
            					}

            					@Override
            					public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
            					}
            					
            					@Override
            					public void onClosed(PlayerEntity player) {
            						super.onClosed(player);
            						if(!isFinished[0]) {
            							player.getServer().execute(() -> {
            								player.openHandledScreen(factoryHolder[0]);
            							});
            						}
            					}
            				};
            			},
            			Text.literal("ROTANDO...").setStyle(style.withColor(TextColor.parse("white").getOrThrow()))
            			);
            	serverPlayer.openHandledScreen(factoryHolder[0]);
            } else {
            	final NamedScreenHandlerFactory[] factoryHolder = new NamedScreenHandlerFactory[1];
            	factoryHolder[0] = new SimpleNamedScreenHandlerFactory(
            			(syncId, playerInventory, playerEntity) -> {
            				return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventoryHolder[0], rows[0]) {
            					@Override
            					public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
            						return true;
            					}

            					@Override
            					public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
            					}
            					
                	            @Override
                	            public void onClosed(PlayerEntity player) {
                	                super.onClosed(player);
            						if(!isFinished[0]) {
            							player.getServer().execute(() -> {
            								player.openHandledScreen(factoryHolder[0]);
            							});
            						}
                	            }
            				};
            			},
            			Text.literal("ROTANDO...").setStyle(style.withColor(TextColor.parse("white").getOrThrow()))
            			);
            	serverPlayer.openHandledScreen(factoryHolder[0]);
            }

            
            
            for (int i = 0; i < 65; i++) {
                if (i < 40) {
                    ticks = 2;
                } else if (i < 56) {
                    ticks = 4;
                } else if (i < 60) {
                    ticks = 6;
                } else if (i < 63) {
                    ticks = 10;
                } else if (i < 64) {
                    ticks = 15;
                } else if (i < 65) {
                    ticks = 20;
                }
                scheduler.schedule(() -> {
                    serverPlayer.getServer().execute(() -> {
                        serverPlayer.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.MASTER, 1.0F, 1.0F);
                        inventoryHolder[0]=LootUtil.setLootItem(inventoryHolder[0],pool.get("item_pool").getAsString(),2);
                        if(isMulti) {
                            inventoryHolder[0]=LootUtil.setLootItem(inventoryHolder[0],pool.get("item_pool").getAsString(),5);
                        }
                        inventoryHolder[0].markDirty();
                    });
                }, total, TimeUnit.MILLISECONDS);
                total += 50 * ticks;
            }
            endInventory(serverPlayer,inventoryHolder,total,isMulti);            
            scheduler.schedule(() -> {
                serverPlayer.getServer().execute(() -> {
                	isFinished[0]=true;
                    serverPlayer.closeHandledScreen();
                    LOGGER.info("FINALIZANDO GACHA");
                    playerGiveRewards(serverPlayer,inventoryHolder[0],isMulti);
                    
                });
            }, total + 5000, TimeUnit.MILLISECONDS);
        }, 4, TimeUnit.SECONDS);
        
        

    }
    
    
    
    public static SimpleInventory fillDefaultInventory(SimpleInventory inventory) {
        Random random = new Random();
        for (int slot = 0; slot < inventory.size(); slot++) {
            double chance = random.nextDouble();
            ItemStack glassPane;
            if (chance < 0.44) {
                glassPane = new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE);
            } else if (chance < 0.88) {
                glassPane = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
            } else {
                glassPane = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
            }
            inventory.setStack(slot, glassPane);            
        }
        return inventory;
    }
    
    public static void loadConfig() {
        File configFile = new File("config/ivogacha/config.json");
        if (!configFile.exists()) {
            loadDefaultConfig();
        }
        try (FileReader reader = new FileReader(configFile)) {
            config = new Gson().fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void loadDefaultConfig() {
        JsonObject defaultConfig = new JsonObject();
        
        JsonObject coords = new JsonObject();
        coords.addProperty("x", 123);
        coords.addProperty("y", 55);
        coords.addProperty("z", -188);
        defaultConfig.add("coords", coords);
        
        JsonArray poolsArray = new JsonArray();
        JsonObject defaultPool = new JsonObject();
        defaultPool.addProperty("slot", 13);
        JsonObject coste = new JsonObject();
        coste.addProperty("namespace", "minecraft");
        coste.addProperty("id", "diamond");
        coste.addProperty("cantidad", 1);
        defaultPool.add("coste",coste);
        defaultPool.addProperty("nombre", "Cofre Gacha");
        defaultPool.addProperty("colorNombre", "#FFFFFF");
        defaultPool.addProperty("lore", "Pool Lore");
        defaultPool.addProperty("colorLore", "#AAAAAA");
        defaultPool.addProperty("chestDisplayCustomModelData", 1);
        defaultPool.addProperty("chestDisplayCustomModelDataMulti",2);
        defaultPool.addProperty("item_pool", "default");
        poolsArray.add(defaultPool);
        
        defaultConfig.add("pools", poolsArray);
        
        File configDir = new File("config/ivogacha");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File animDir = new File("config/ivogacha/pools");
        if (!animDir.exists()) {
            animDir.mkdirs();
        }
        File configFile = new File(configDir, "config.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(defaultConfig, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        config = defaultConfig;
    }

    public static boolean playerHasItem(PlayerEntity jugador, Item objeto, int cantidad) {
        int total = 0;
        for (ItemStack stack : jugador.getInventory().main) {
            if (stack.getItem() == objeto) {
                total += stack.getCount();
                if (total >= cantidad) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static void playerClearItems(PlayerEntity jugador, Item objeto, int cantidad) {
        int total = cantidad;
        for (ItemStack stack : jugador.getInventory().main) {
            if (stack.getItem() == objeto) {
            	if(stack.getCount()>total) {
                    stack.decrement(total);
                    total=0;;
            	}
            	else {
            		total-=stack.getCount();
                    stack.decrement(stack.getCount());
            	}
                if (total <= 0) {
                    return;
                }
            }
        }
    }
    
    public static void playerGiveRewards(PlayerEntity jugador, SimpleInventory inventario, boolean isMulti) {
        List<Integer> slots = new ArrayList<>();

        if (!isMulti) {
            slots.add(13);
        } else {
            slots.addAll(Arrays.asList(11, 12, 13, 14, 15, 38, 39, 40, 41, 42));
        }
        
        for (int i = 0; i < slots.size(); i++) {
            LootEntry entry = LootUtil.getLootEntryFromSlot(inventario, slots.get(i));
            LOGGER.info(jugador.getName().getLiteralString() + " HA CONSEGUIDO EN EL GACHA: " + entry.id);
            // Procesar recompensa
            if (entry.Tipo.toLowerCase().equals("pokemon")) {
                Pokemon pokemon = PokemonSpecies.INSTANCE.getByName(entry.pokemon_properties.especie).create(1);
                PokemonProperties propiedades = new PokemonProperties();
                pokemon.setIV(Stats.HP, entry.pokemon_properties.HP_iv);
                pokemon.setIV(Stats.ATTACK, entry.pokemon_properties.ATTACK_iv);
                pokemon.setIV(Stats.DEFENCE, entry.pokemon_properties.DEFENCE_iv);
                pokemon.setIV(Stats.SPECIAL_ATTACK, entry.pokemon_properties.SPECIAL_ATTACK_iv);
                pokemon.setIV(Stats.SPECIAL_DEFENCE, entry.pokemon_properties.SPECIAL_DEFENCE_iv);
                pokemon.setIV(Stats.SPEED, entry.pokemon_properties.SPEED_iv);
                propiedades.setIvs(pokemon.getIvs());
                propiedades.setSpecies(entry.pokemon_properties.especie);
                propiedades.setForm(entry.pokemon_properties.form);
                propiedades.setShiny(entry.pokemon_properties.shiny);
                propiedades.setAbility(entry.pokemon_properties.ability);
                propiedades.setMoves(entry.pokemon_properties.moves);
                pokemon = propiedades.create();
                Cobblemon.INSTANCE.getStorage().getParty((ServerPlayerEntity) jugador).add(pokemon);
                Cobblemon.playerDataManager.getPokedexData((ServerPlayerEntity) jugador);
                PokedexManager pokedexData = Cobblemon.playerDataManager.getPokedexData((ServerPlayerEntity) jugador);
                Method catchMethod = null;
                try {
                    catchMethod = Cobblemon.playerDataManager.getPokedexData((ServerPlayerEntity) jugador)
                            .getClass().getMethod("catch", pokemon.getClass());
                } catch (NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
                try {
                    catchMethod.invoke(pokedexData, pokemon);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                ItemStack stack = LootUtil.createItemStack(entry.namespace, entry.id, entry.cantidad, entry.custom_model_data, entry.custom_data,entry.displayName);
                jugador.giveItemStack(stack);
            }
            
            //nombre de la recompensa
            Text rewardName = Text.literal("");
            if (entry.Tipo.toLowerCase().equals("pokemon")) {
            	if (entry.displayName != null && !entry.displayName.isEmpty()) {
            		rewardName= Text.literal(entry.displayName).formatted(Formatting.YELLOW);
            	}else {
            		if(entry.pokemon_properties.shiny) {
            			rewardName= Text.literal(entry.pokemon_properties.especie.substring(0, 1).toUpperCase()+entry.pokemon_properties.especie.substring(1).toLowerCase()+ "Shiny").formatted(Formatting.YELLOW);
            		}
            		else {
            			rewardName= Text.literal(entry.pokemon_properties.especie.substring(0, 1).toUpperCase()+entry.pokemon_properties.especie.substring(1).toLowerCase()).formatted(Formatting.YELLOW);
            		}
            	}
            	
            } else {
            	if (entry.displayName != null && !entry.displayName.isEmpty()) {
            		rewardName= Text.literal(entry.displayName).formatted(Formatting.WHITE);
            	}
            	else {
            		ItemStack tempStack = LootUtil.createItemStack(entry.namespace, entry.id, entry.cantidad, entry.custom_model_data, entry.custom_data,entry.displayName);
            		rewardName= Text.literal(tempStack.getName().getString()).formatted(Formatting.WHITE);
            	}

            }

            MutableText mensaje = Text.literal("¡Has obtenido: ")
                    .formatted(Formatting.GREEN)
                    .append(rewardName)
                    .append(Text.literal("!")
                            .formatted(Formatting.GREEN));
            jugador.sendMessage(mensaje);
        }
    }

    public static void endInventory(ServerPlayerEntity serverPlayer, SimpleInventory[] inventoryHolder, int total, boolean isMulti) {
        // Paneles para replaceItems
        ItemStack redGlassPane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        ItemStack blueGlassPane = new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        
        List<Integer> slots = new ArrayList<>();
        List<Integer> slots2 = new ArrayList<>();
        if (!isMulti) {
            slots.addAll(Arrays.asList(9, 10, 11, 12, 14, 15, 16, 17));
        } else {
            slots.addAll(Arrays.asList(9, 10, 16, 17));
            slots2.addAll(Arrays.asList(36, 37, 43, 44));
        }
        
        List<Integer> orderedSlots = orderSlotsFromExtremesToCenter(slots);
        List<Integer> orderedSlots2 = new ArrayList<>();
        if (isMulti) {
            orderedSlots2 = orderSlotsFromExtremesToCenter(slots2);
        }
        
        for (int j = 0; j < 9; j++) {
            if (j % 2 == 0) {
                scheduler.schedule(() -> {
                    serverPlayer.getServer().execute(() -> {
                        replaceItems(inventoryHolder[0], redGlassPane, blueGlassPane);
                    });
                }, total + j * 500, TimeUnit.MILLISECONDS);
            } else {
                scheduler.schedule(() -> {
                    serverPlayer.getServer().execute(() -> {
                        replaceItems(inventoryHolder[0], blueGlassPane, redGlassPane);
                    });
                }, total + j * 500, TimeUnit.MILLISECONDS);
            }
            
            if (j >= 2 && j<orderedSlots.size()+2) {
                int pairIndex = j - 2; 
                if (pairIndex * 2 + 1 < orderedSlots.size()) {
                    int slot1 = orderedSlots.get(pairIndex * 2);
                    int slot2 = orderedSlots.get(pairIndex * 2 + 1);
                    scheduler.schedule(() -> {
                        serverPlayer.getServer().execute(() -> {
                            // Cada slot recibe un panel aleatorio (cada uno se genera de forma independiente)
                            inventoryHolder[0].setStack(slot1, getRandomGlassPane());
                            inventoryHolder[0].setStack(slot2, getRandomGlassPane());
                        });
                    }, total + j * 500, TimeUnit.MILLISECONDS);
                }
                // Si es modo multi, se actualiza el segundo grupo de slots de igual forma
                if (isMulti && !orderedSlots2.isEmpty() && pairIndex * 2 + 1 < orderedSlots2.size()) {
                    int slot1 = orderedSlots2.get(pairIndex * 2);
                    int slot2 = orderedSlots2.get(pairIndex * 2 + 1);
                    scheduler.schedule(() -> {
                        serverPlayer.getServer().execute(() -> {
                            inventoryHolder[0].setStack(slot1, getRandomGlassPane());
                            inventoryHolder[0].setStack(slot2, getRandomGlassPane());
                        });
                    }, total + j * 500, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private static ItemStack getRandomGlassPane() {
        double chance = Math.random(); // o random.nextDouble() si dispones de una instancia Random
        if (chance < 0.44) {
            return new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE);
        } else if (chance < 0.88) {
            return new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        } else {
            return new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        }
    }

    private static List<Integer> orderSlotsFromExtremesToCenter(List<Integer> slots) {
        List<Integer> sorted = new ArrayList<>(slots);
        Collections.sort(sorted);
        List<Integer> result = new ArrayList<>();
        int left = 0;
        int right = sorted.size() - 1;
        while (left <= right) {
            if (left == right) {
                result.add(sorted.get(left));
            } else {
                result.add(sorted.get(left));
                result.add(sorted.get(right));
            }
            left++;
            right--;
        }
        return result;
    }

    public static void replaceItems(SimpleInventory inventory, ItemStack itemOriginal, ItemStack itemNuevo) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == itemOriginal.getItem()) {
                ItemStack nuevoStack = new ItemStack(itemNuevo.getItem(), stack.getCount());
                inventory.setStack(i, nuevoStack);
            }
        }
        inventory.markDirty();
    }
    
    
    
    
    
    
    
    
    
    
    
    
}