package com.goodbird.cnpccobblemonaddon.quest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.goodbird.cnpccobblemonaddon.util.ClientPartyProxy;
import com.goodbird.cnpccobblemonaddon.util.NBTUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestInterface;

import java.util.*;

public class QuestPokeTeam extends QuestInterface {
    public List<PokemonEntry> targets = new ArrayList<>();

    @Override
    public void readAdditionalSaveData(HolderLookup.Provider provider, CompoundTag compound) {
        targets = NBTUtils.getNBTList(PokemonEntry.class, compound.getList("QuestTeamTargets", 10));
    }

    @Override
    public void addAdditionalSaveData(HolderLookup.Provider provider, CompoundTag compound) {
        compound.put("QuestTeamTargets", NBTUtils.tagListToNBT(targets));
    }

    @Override
    public boolean isCompleted(Player player) {
        PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player.getUUID(), player.level().registryAccess());
        for(PokemonEntry entry : targets) {
            boolean hasInTeam = false;
            for (Pokemon pokemon : store) {
                if(entry.matches(pokemon)) {
                    hasInTeam = true;
                }
            }
            if(!hasInTeam) {
                return false;
            }
        }
        return true;

    }

    @Override
    public void handleComplete(Player player) {
    }

    @Override
    public IQuestObjective[] getObjectives(Player player) {
        List<IQuestObjective> list = new ArrayList<>();
        for(PokemonEntry entry : targets){
            list.add(new QuestPokeTeamObjective(player, entry));
        }
        return list.toArray(new IQuestObjective[list.size()]);
    }

    class QuestPokeTeamObjective implements IQuestObjective{
        private final Player player;
        private final PokemonEntry pokemonEntry;
        public QuestPokeTeamObjective(Player player, PokemonEntry pokemonEntry) {
            this.player = player;
            this.pokemonEntry = pokemonEntry;
        }

        @Override
        public int getProgress() {
            // CLIENT SIDE
            if (player.level().isClientSide()) {
                return ClientPartyProxy.checkLocalParty(this.pokemonEntry);
            }

            // SERVER SIDE
            try {
                PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player.getUUID(), player.level().registryAccess());
                boolean hasPokemon = false;

                // Check if they have the Pokemon
                for(Pokemon pokemon : store) {
                    if(pokemonEntry.matches(pokemon)) {
                        hasPokemon = true;
                        break;
                    }
                }

                // Get CustomNPCs native data storage
                PlayerData playerData = PlayerData.get(player);
                if (playerData != null && playerData.questData != null) {
                    QuestData questData = playerData.questData.activeQuests.get(QuestPokeTeam.this.questId);
                    if (questData != null) {

                        if (hasPokemon) {
                            // The player has the Pokemon! Let's check the timestamp.
                            long foundTime = questData.extraData.getLong("poke_found_time");

                            if (foundTime == 0) {
                                // First time seeing it! Start the 1.5-second timer
                                questData.extraData.putLong("poke_found_time", System.currentTimeMillis());
                                return 0; // Pretend it's not complete yet to avoid the crash
                            } else if (System.currentTimeMillis() - foundTime > 1500) {
                                // 1.5 seconds have passed. The Cobblemon packet is safely gone.
                                return 1; // Officially complete the quest!
                            } else {
                                // Still waiting for the timer...
                                return 0;
                            }
                        } else {
                            // If they lose the Pokemon before the timer finishes, reset the timer
                            questData.extraData.putLong("poke_found_time", 0);
                        }
                    }
                }

                // Fallback in case CustomNPCs data isn't ready
                return hasPokemon ? 1 : 0;

            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        public void setProgress(int progress) {
            throw new CustomNPCsException("Cant set the progress of PokeTeamQuests");
        }

        @Override
        public int getMaxProgress() {
            return 1;
        }

        @Override
        public boolean isCompleted() {
            return getProgress() == 1;
        }

        @Override
        public String getText() {
            return getMCText().getString();
        }

        @Override
        public Component getMCText() {
            MutableComponent text = Component.translatable("objective.poketeam").append(" ");
            if(pokemonEntry.isShiny()){
                text.append(Component.translatable("poketype.shiny").append(" "));
            }
            if(pokemonEntry.getMinLevel()!=0){
                text.append(Component.translatable("poketype.minlevel", pokemonEntry.getMinLevel()).append(" "));
            }
            Species species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(pokemonEntry.getType()));
            text.append(species==null?Component.translatable(pokemonEntry.getType()):species.getTranslatedName());
            return text.append(": " + getProgress() + "/" + getMaxProgress());
        }
    }

}
