/*
 * Open Parties and Claims - adds chunk claims and player parties to Minecraft
 * Copyright (C) 2022-2026, Xaero <xaero1996@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU Lesser General Public License
 * (LGPL-3.0-only) as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received copies of the GNU Lesser General Public License
 * and the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package xaero.pac.common.server.parties.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.claims.player.IPlayerClaimPosList;
import xaero.pac.common.claims.player.IPlayerDimensionClaims;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.IServerClaimsManager;
import xaero.pac.common.server.claims.IServerDimensionClaimsManager;
import xaero.pac.common.server.claims.IServerRegionClaims;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.IPartyManager;
import xaero.pac.common.server.parties.party.IServerParty;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.data.ServerPlayerData;
import xaero.pac.common.server.player.localization.AdaptiveLocalizer;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public class CreatePartyCommand {
	
	public void register(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection environment, CommandRequirementProvider commandRequirementProvider) {
		Predicate<CommandSourceStack> requirement = commandRequirementProvider.getNonMemberRequirement(p -> true, false);
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(PartyCommandRegister.COMMAND_PREFIX).requires(c -> ServerConfig.CONFIG.partiesEnabled.get()).then(Commands.literal("create")
				.requires(requirement)
				// [Team Claims] optional team name, which becomes the party name and the team sub-config ID
				.then(Commands.argument("teamname", StringArgumentType.greedyString())
						.executes(context -> executeCreate(context, StringArgumentType.getString(context, "teamname"))))
				.executes(context -> executeCreate(context, null)));
		dispatcher.register(command);
	}

	private int executeCreate(CommandContext<CommandSourceStack> context, @Nullable String teamName) {
		Entity entity = context.getSource().getEntity();
		if(entity == null || !(entity instanceof Player))
			return 0;
		ServerPlayer player = (ServerPlayer) entity;
		ServerPlayerData serverPlayerData = (ServerPlayerData) ServerPlayerData.from(player);
		GameProfile ownerProfile = serverPlayerData.getPartiesImpersonatedPlayerProfile();
		if(ownerProfile == null)
			ownerProfile = player.getGameProfile();
		MinecraftServer server = context.getSource().getServer();
		IServerData<IServerClaimsManager<IPlayerChunkClaim, IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>, IServerDimensionClaimsManager<IServerRegionClaims>>, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> serverData = ServerData.from(server);
		AdaptiveLocalizer adaptiveLocalizer = serverData.getAdaptiveLocalizer();
		IPartyManager<IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> partyManager = serverData.getPartyManager();
		// [Team Claims] with a team name, the shared Team Claims create path validates the name, creates
		// and names the party, sets up the team config and reports the outcome (same as /teamclaims create)
		xaero.pac.common.server.claims.TeamClaimsIntegration.TeamClaimsHandler tcHandler =
				xaero.pac.common.server.claims.TeamClaimsIntegration.getHandler();
		if(teamName != null && tcHandler != null)
			return tcHandler.createPartyWithTeamName(context.getSource(), player, ownerProfile, teamName);
		partyManager.createPartyForOwner(ownerProfile);
		// [Team Claims] without Team Claims active, the team name is just the party name
		if(teamName != null && !teamName.isBlank())
			serverData.getPlayerConfigManager().getLoadedConfig(ownerProfile.getId())
					.tryToSet(PlayerConfigOptions.PARTY_NAME, teamName);
		player.sendSystemMessage(adaptiveLocalizer.getFor(player, "gui.xaero_parties_party_created"));
		serverData.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(player, serverData, false);
		return 1;
	}

}
