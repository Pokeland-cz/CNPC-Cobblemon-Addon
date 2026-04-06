package com.goodbird.cnpccobblemonaddon.util;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.goodbird.cnpccobblemonaddon.quest.PokemonEntry;

public class ClientPartyProxy {

    // This method is ONLY EVER called on the client
    public static int checkLocalParty(PokemonEntry pokemonEntry) {
        try {
            // Get the client-side representation of the player's party.
            var clientParty = CobblemonClient.INSTANCE.getStorage().getParty();

            for (Pokemon pokemon : clientParty) {
                if (pokemonEntry.matches(pokemon)) {
                    return 1;
                }
            }
        } catch (Exception e) {
            // Failsafe
        }
        return 0;
    }
}
