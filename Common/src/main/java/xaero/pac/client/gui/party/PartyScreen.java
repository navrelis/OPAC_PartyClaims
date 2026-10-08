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

package xaero.pac.client.gui.party;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.controls.keybinding.IKeyBindingHelper;
import xaero.pac.client.gui.MainMenu;
import xaero.pac.client.gui.XPACScreen;
import xaero.pac.client.parties.party.ClientReceivedPartyInvite;
import xaero.pac.client.parties.party.IClientParty;
import xaero.pac.client.parties.party.IClientPartyAllyInfo;
import xaero.pac.client.parties.party.IClientPartyMemberDynamicInfoSyncableStorage;
import xaero.pac.client.parties.party.IClientPartyStorage;
import xaero.pac.client.world.capability.ClientWorldMainCapability;
import xaero.pac.client.world.capability.api.ClientWorldCapabilityTypes;
import xaero.pac.common.packet.config.PlayerConfigOptionValuePacket;
import xaero.pac.common.packet.config.ServerboundPlayerConfigOptionValuePacket;
import xaero.pac.common.packet.parties.ServerboundPartyInviteDeclinePacket;
import xaero.pac.common.packet.parties.ServerboundPartyInvitesRequestPacket;
import xaero.pac.common.parties.party.IPartyMemberDynamicInfoSyncable;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.platform.Services;
import xaero.pac.common.server.player.config.PlayerConfigOptionSpec;
import xaero.pac.common.server.player.config.api.PlayerConfigType;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The screen that lets a player manage parties without typing commands: creating a party, answering invitations,
 * and, for members, managing the members, the invitations and the name of the party or leaving it.
 * All of its data is read from the client party storage, so it updates by itself when the server syncs a change.
 */
public class PartyScreen extends XPACScreen implements PartyEntryList.Actions {

	private static final int INVITES_REQUEST_INTERVAL_TICKS = 40;
	private static final long INVITES_REQUEST_MIN_INTERVAL_MILLIS = 1000;
	private static final int MAX_SUGGESTIONS = 6;
	private static final int MAX_CONTENT_WIDTH = 320;
	private static final int ROW_GAP = 4;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SUGGESTION_HEIGHT = 14;
	private static final int INVALID_TEXT_COLOR = 0xFFFF5555;
	private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;
	private static final int VALUE_COLOR = 0xFFAAAAAA;
	private static final int MAX_PLAYER_NAME_LENGTH = 32;

	private static final Component TITLE = Component.translatable("gui.xaero_pac_party_screen_title");
	private static final Component CREATE_TITLE = Component.translatable("gui.xaero_pac_party_screen_create_title");
	private static final Component CREATE_NAME_HINT = Component.translatable("gui.xaero_pac_party_screen_create_name_hint");
	private static final Component CREATE = Component.translatable("gui.xaero_pac_party_screen_create");
	private static final Component INVITES_TITLE = Component.translatable("gui.xaero_pac_party_screen_invites_title");
	private static final Component NO_INVITES = Component.translatable("gui.xaero_pac_party_screen_no_invites");
	private static final Component INVITE_HINT = Component.translatable("gui.xaero_pac_party_screen_invite_hint");
	private static final Component INVITE = Component.translatable("gui.xaero_pac_party_screen_invite");
	private static final Component RENAME_HINT = Component.translatable("gui.xaero_pac_party_screen_rename_hint");
	private static final Component SAVE = Component.translatable("gui.xaero_pac_party_screen_rename_save");
	private static final Component LEAVE = Component.translatable("gui.xaero_pac_party_screen_leave");
	private static final Component DISBAND = Component.translatable("gui.xaero_pac_party_screen_disband");
	private static final Tooltip NAME_INVALID = Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_name_invalid"));
	private static final Tooltip PLAYER_NAME_INVALID = Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_player_name_invalid"));
	private static final Component BACK = Component.translatable("gui.xaero_pac_back");

	private enum State {
		NO_MOD, NO_PARTIES, SYNCING, NO_PARTY, IN_PARTY
	}

	/**
	 * Everything that decides which widgets the screen has. A change of any of it rebuilds the widgets.
	 */
	private record LayoutKey(State state, boolean member, boolean canInvite, boolean owner, UUID partyId, UUID ownerId) {
	}

	private State state = State.NO_MOD;
	private LayoutKey layoutKey;
	private int contentWidth;
	private int contentLeft;
	private int headerBottom;
	private int createLabelY;
	private int infoLineY;
	private int invitesLabelY;
	private int messageY;
	private int suggestionRowY;

	private PartyEntryList list;
	private List<PartyRow> displayedRows;
	private EditBox createBox;
	private Button createButton;
	private EditBox inviteBox;
	private Button inviteButton;
	private EditBox renameBox;
	private Button renameButton;
	private Button leaveOrDisbandButton;
	private final List<Button> suggestionButtons = new ArrayList<>();
	private List<String> displayedSuggestions = List.of();
	private String displayedSuggestionFilter;

	private String createText = "";
	private String inviteText = "";
	private String renameText;
	private int invitesRequestCounter;
	private long lastInvitesRequestTime;

	public PartyScreen(Screen escape, Screen parent) {
		super(escape, parent, TITLE);
	}

	private static IClientPartyStorage<IClientPartyAllyInfo, IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>, IClientPartyMemberDynamicInfoSyncableStorage<IPartyMemberDynamicInfoSyncable>> getPartyStorage() {
		return OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage();
	}

	private State computeState() {
		if(minecraft.level == null)
			return State.NO_MOD;
		ClientWorldMainCapability mainCap = (ClientWorldMainCapability) OpenPartiesAndClaims.INSTANCE.getCapabilityHelper().getCapability(minecraft.level, ClientWorldCapabilityTypes.MAIN_CAP);
		if(!mainCap.getClientWorldData().serverHasMod())
			return State.NO_MOD;
		if(!mainCap.getClientWorldData().serverHasPartiesEnabled())
			return State.NO_PARTIES;
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		if(partyStorage.isLoading())
			return State.SYNCING;
		return partyStorage.getParty() == null ? State.NO_PARTY : State.IN_PARTY;
	}

	@Nullable
	private IPartyMember getLocalMember() {
		IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = getPartyStorage().getParty();
		if(party == null || minecraft.player == null)
			return null;
		return party.getMemberInfo(minecraft.player.getUUID());
	}

	private LayoutKey computeLayoutKey(State state) {
		if(state != State.IN_PARTY)
			return new LayoutKey(state, false, false, false, null, null);
		IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = getPartyStorage().getParty();
		IPartyMember local = getLocalMember();
		return new LayoutKey(state, local != null, local != null && PartyRules.canInviteAndKick(local), local != null && PartyRules.canRename(local), party.getId(), party.getOwner().getUUID());
	}

	private String getCurrentPartyName() {
		String partyName = getPartyStorage().getPartyName();
		return partyName == null ? "" : partyName;
	}

	@Override
	protected void init() {
		super.init();
		state = computeState();
		layoutKey = computeLayoutKey(state);
		list = null;
		displayedRows = null;
		createBox = null;
		createButton = null;
		inviteBox = null;
		inviteButton = null;
		renameBox = null;
		renameButton = null;
		leaveOrDisbandButton = null;
		suggestionButtons.clear();
		displayedSuggestions = List.of();
		displayedSuggestionFilter = null;
		contentWidth = Math.min(width - 16, MAX_CONTENT_WIDTH);
		contentLeft = (width - contentWidth) / 2;
		if(state == State.NO_PARTY)
			initNoParty();
		else if(state == State.IN_PARTY)
			initInParty();
		else
			initMessageOnly();
		updateWidgets();
	}

	private void initMessageOnly() {
		messageY = 40;
		addBackButton(width / 2 - 100, height - 26, 200);
	}

	private void initNoParty() {
		createLabelY = 18;
		int createButtonWidth = font.width(CREATE) + 20;
		createBox = new EditBox(font, contentLeft, createLabelY + font.lineHeight + 3, contentWidth - createButtonWidth - ROW_GAP, BUTTON_HEIGHT, CREATE_NAME_HINT);
		createBox.setMaxLength(PartyRules.getPartyNameMaxLength());
		createBox.setHint(CREATE_NAME_HINT);
		createBox.setValue(createText);
		createBox.setResponder(text -> {
			createText = text;
			updateWidgets();
		});
		addRenderableWidget(createBox);
		createButton = addRenderableWidget(Button.builder(CREATE, b -> onCreateButton())
				.bounds(contentLeft + contentWidth - createButtonWidth, createBox.getY(), createButtonWidth, BUTTON_HEIGHT).build());
		invitesLabelY = createBox.getY() + BUTTON_HEIGHT + 9;
		int listTop = invitesLabelY + font.lineHeight + 4;
		int listBottom = height - 32;
		list = addRenderableWidget(new PartyEntryList(minecraft, width, Math.max(PartyEntryList.ITEM_HEIGHT, listBottom - listTop), listTop, getListRowWidth(), this));
		addBackButton(width / 2 - 100, height - 26, 200);
		maybeRequestInvites();
	}

	private int getListRowWidth() {
		return contentWidth - 16;
	}

	private void initInParty() {
		IPartyMember local = getLocalMember();
		boolean owner = local != null && PartyRules.canRename(local);
		boolean canInvite = local != null && PartyRules.canInviteAndKick(local);
		int y = 14;
		if(owner) {
			int saveWidth = font.width(SAVE) + 20;
			String currentName = getCurrentPartyName();
			renameBox = new EditBox(font, contentLeft, y, contentWidth - saveWidth - ROW_GAP, BUTTON_HEIGHT, RENAME_HINT);
			renameBox.setMaxLength(PartyRules.getPartyNameMaxLength());
			renameBox.setHint(RENAME_HINT);
			renameBox.setValue(renameText == null ? currentName : renameText);
			renameBox.setResponder(text -> {
				renameText = text.equals(getCurrentPartyName()) ? null : text;
				updateWidgets();
			});
			addRenderableWidget(renameBox);
			renameButton = addRenderableWidget(Button.builder(SAVE, b -> onRenameButton())
					.bounds(contentLeft + contentWidth - saveWidth, y, saveWidth, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + ROW_GAP;
		} else {
			messageY = y + 4;
			y += font.lineHeight + 8;
		}
		int infoLines = font.split(getInfoComponent(true), contentWidth).size();
		infoLineY = y;
		y += infoLines * (font.lineHeight + 2) + 2;
		headerBottom = y;
		int bottomRowY = height - 26;
		int footerTop = bottomRowY;
		if(canInvite) {
			int inviteRowY = bottomRowY - BUTTON_HEIGHT - ROW_GAP;
			suggestionRowY = inviteRowY - SUGGESTION_HEIGHT - ROW_GAP;
			footerTop = suggestionRowY;
			int inviteButtonWidth = font.width(INVITE) + 20;
			inviteBox = new EditBox(font, contentLeft, inviteRowY, contentWidth - inviteButtonWidth - ROW_GAP, BUTTON_HEIGHT, INVITE_HINT);
			inviteBox.setMaxLength(MAX_PLAYER_NAME_LENGTH);
			inviteBox.setHint(INVITE_HINT);
			inviteBox.setValue(inviteText);
			inviteBox.setResponder(text -> {
				inviteText = text;
				updateWidgets();
			});
			addRenderableWidget(inviteBox);
			inviteButton = addRenderableWidget(Button.builder(INVITE, b -> onInviteButton())
					.bounds(contentLeft + contentWidth - inviteButtonWidth, inviteRowY, inviteButtonWidth, BUTTON_HEIGHT).build());
		}
		int listTop = headerBottom + 2;
		int listHeight = Math.max(PartyEntryList.ITEM_HEIGHT, footerTop - ROW_GAP - listTop);
		list = addRenderableWidget(new PartyEntryList(minecraft, width, listHeight, listTop, getListRowWidth(), this));
		int halfWidth = (contentWidth - ROW_GAP) / 2;
		boolean isOwner = local != null && local.isOwner();
		leaveOrDisbandButton = addRenderableWidget(Button.builder(isOwner ? DISBAND : LEAVE, b -> onLeaveOrDisbandButton())
				.bounds(contentLeft, bottomRowY, halfWidth, BUTTON_HEIGHT).build());
		leaveOrDisbandButton.active = local != null;
		addBackButton(contentLeft + contentWidth - halfWidth, bottomRowY, halfWidth);
	}

	/**
	 * Does not focus a text field when opening, so that the key that opens the screen also closes it.
	 */
	@Override
	protected void setInitialFocus() {
	}

	private void addBackButton(int x, int y, int buttonWidth) {
		addRenderableWidget(Button.builder(BACK, b -> goBack()).bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
	}

	// ---- actions ----

	private void onCreateButton() {
		String name = createText.trim();
		String command = PartyScreenCommands.create(name);
		if(command == null)
			return;
		PartyScreenCommands.send(minecraft, command);
		createText = "";
		createBox.setValue("");
	}

	private void onInviteButton() {
		invitePlayer(inviteText);
	}

	private void invitePlayer(String playerName) {
		String command = PartyScreenCommands.invite(playerName);
		if(command == null)
			return;
		PartyScreenCommands.send(minecraft, command);
		inviteText = "";
		if(inviteBox != null)
			inviteBox.setValue("");
	}

	private void onRenameButton() {
		String name = getRenameValue();
		if(!isRenameAllowed(name))
			return;
		PlayerConfigOptionValuePacket.Entry entry = PlayerConfigOptionValuePacket.Entry.of((PlayerConfigOptionSpec<String>) PlayerConfigOptions.PARTY_NAME, name);
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToServer(
				new ServerboundPlayerConfigOptionValuePacket(PlayerConfigType.PLAYER, null, null, Lists.newArrayList(entry)));
	}

	private void onLeaveOrDisbandButton() {
		IPartyMember local = getLocalMember();
		if(local == null)
			return;
		String partyName = getCurrentPartyName();
		if(local.isOwner()) {
			openConfirmation(Component.translatable("gui.xaero_pac_party_screen_disband_confirm_title"),
					Component.translatable("gui.xaero_pac_party_screen_disband_confirm_message", partyName),
					DISBAND, () -> PartyScreenCommands.send(minecraft, PartyScreenCommands.destroyConfirmed()));
		} else {
			openConfirmation(Component.translatable("gui.xaero_pac_party_screen_leave_confirm_title"),
					Component.translatable("gui.xaero_pac_party_screen_leave_confirm_message", partyName),
					LEAVE, () -> PartyScreenCommands.send(minecraft, PartyScreenCommands.leave()));
		}
	}

	private void openConfirmation(Component title, Component message, Component confirmLabel, Runnable onConfirm) {
		minecraft.setScreen(new ConfirmScreen(accepted -> {
			minecraft.setScreen(this);
			if(accepted)
				onConfirm.run();
		}, title, message, confirmLabel, CommonComponents.GUI_CANCEL));
	}

	@Override
	public void onRankClicked(PartyRow.Member member) {
		PartyMemberRank nextRank = member.nextRank();
		if(nextRank != null)
			PartyScreenCommands.send(minecraft, PartyScreenCommands.rank(nextRank, member.name()));
	}

	@Override
	public void onKickClicked(PartyRow.Member member) {
		PartyScreenCommands.send(minecraft, PartyScreenCommands.kick(member.name()));
	}

	@Override
	public void onMakeOwnerClicked(PartyRow.Member member) {
		openConfirmation(Component.translatable("gui.xaero_pac_party_screen_make_owner_confirm_title"),
				Component.translatable("gui.xaero_pac_party_screen_make_owner_confirm_message", member.name()),
				Component.translatable("gui.xaero_pac_party_screen_make_owner"),
				() -> PartyScreenCommands.send(minecraft, PartyScreenCommands.transferConfirmed(member.name())));
	}

	@Override
	public void onCancelInviteClicked(PartyRow.SentInvite invite) {
		PartyScreenCommands.send(minecraft, PartyScreenCommands.kick(invite.name()));
	}

	@Override
	public void onAcceptClicked(PartyRow.ReceivedInvite invite) {
		PartyScreenCommands.send(minecraft, PartyScreenCommands.join(invite.partyId()));
	}

	@Override
	public void onDeclineClicked(PartyRow.ReceivedInvite invite) {
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToServer(new ServerboundPartyInviteDeclinePacket(invite.partyId()));
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		List<ClientReceivedPartyInvite> remaining = new ArrayList<>(partyStorage.getReceivedInvites());
		remaining.removeIf(received -> received.partyId().equals(invite.partyId()));
		partyStorage.setReceivedInvites(remaining);
	}

	// ---- data ----

	private void maybeRequestInvites() {
		long now = System.currentTimeMillis();
		if(now - lastInvitesRequestTime < INVITES_REQUEST_MIN_INTERVAL_MILLIS)
			return;
		lastInvitesRequestTime = now;
		invitesRequestCounter = 0;
		OpenPartiesAndClaims.INSTANCE.getPacketHandler().sendToServer(new ServerboundPartyInvitesRequestPacket());
	}

	private boolean isCreateAllowed() {
		String name = createText.trim();
		return name.isEmpty() || PartyRules.isValidPartyName(name);
	}

	private String getRenameValue() {
		return renameBox == null ? "" : renameBox.getValue().trim();
	}

	private boolean isRenameAllowed(String name) {
		return PartyRules.isValidPartyName(name) && !name.equals(getCurrentPartyName());
	}

	private boolean isInviteNameAllowed() {
		return PartyRules.isSafePlayerName(inviteText);
	}

	private List<PartyRow> computeRows() {
		List<PartyRow> rows = new ArrayList<>();
		if(state == State.NO_PARTY) {
			for(ClientReceivedPartyInvite invite : getPartyStorage().getReceivedInvites())
				rows.add(new PartyRow.ReceivedInvite(invite.partyId(), invite.partyName(), invite.ownerName()));
			if(rows.isEmpty())
				rows.add(new PartyRow.Message(NO_INVITES));
			return rows;
		}
		IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = getPartyStorage().getParty();
		if(party == null)
			return rows;
		IPartyMember local = getLocalMember();
		List<IPartyMember> members = new ArrayList<>(party.getTypedMemberInfoStream().toList());
		members.sort(Comparator.comparing(IPartyMember::isOwner).reversed()
				.thenComparing(Comparator.comparing((IPartyMember member) -> member.getRank().ordinal()).reversed())
				.thenComparing(member -> member.getUsername().toLowerCase(Locale.ROOT)));
		for(IPartyMember member : members) {
			boolean safeName = PartyRules.isSafePlayerName(member.getUsername());
			boolean self = minecraft.player != null && member.getUUID().equals(minecraft.player.getUUID());
			PartyMemberRank nextRank = local != null && safeName ? PartyRules.getNextRank(local, member) : null;
			boolean canKick = local != null && safeName && PartyRules.canKick(local, member);
			boolean canTransfer = local != null && safeName && PartyRules.canTransferOwnership(local, member);
			rows.add(new PartyRow.Member(member.getUUID(), member.getUsername(), member.getRank(), member.isOwner(), self, nextRank, canKick, canTransfer));
		}
		List<IPartyPlayerInfo> invites = new ArrayList<>(party.getTypedInvitedPlayersStream().toList());
		if(!invites.isEmpty()) {
			invites.sort(Comparator.comparing(invite -> invite.getUsername().toLowerCase(Locale.ROOT)));
			rows.add(new PartyRow.Header(Component.translatable("gui.xaero_pac_party_screen_pending_invites", invites.size())));
			for(IPartyPlayerInfo invite : invites) {
				boolean canCancel = local != null && PartyRules.canInviteAndKick(local) && PartyRules.isSafePlayerName(invite.getUsername());
				rows.add(new PartyRow.SentInvite(invite.getUUID(), invite.getUsername(), canCancel));
			}
		}
		return rows;
	}

	private List<String> computeSuggestions() {
		ClientPacketListener connection = minecraft.getConnection();
		IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = getPartyStorage().getParty();
		if(connection == null || party == null || minecraft.player == null)
			return List.of();
		String filter = inviteText.toLowerCase(Locale.ROOT);
		List<String> suggestions = new ArrayList<>();
		for(PlayerInfo playerInfo : connection.getOnlinePlayers()) {
			String name = playerInfo.getProfile().getName();
			UUID id = playerInfo.getProfile().getId();
			if(!PartyRules.isSafePlayerName(name) || id.equals(minecraft.player.getUUID()))
				continue;
			if(party.getMemberInfo(id) != null || party.isInvited(id))
				continue;
			if(!filter.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filter))
				continue;
			suggestions.add(name);
		}
		suggestions.sort(String.CASE_INSENSITIVE_ORDER);
		return suggestions.size() > MAX_SUGGESTIONS ? List.copyOf(suggestions.subList(0, MAX_SUGGESTIONS)) : List.copyOf(suggestions);
	}

	private void refreshSuggestions() {
		if(inviteBox == null)
			return;
		List<String> suggestions = computeSuggestions();
		if(suggestions.equals(displayedSuggestions) && inviteText.equals(displayedSuggestionFilter))
			return;
		displayedSuggestions = suggestions;
		displayedSuggestionFilter = inviteText;
		for(Button button : suggestionButtons)
			removeWidget(button);
		suggestionButtons.clear();
		int x = contentLeft;
		for(String name : suggestions) {
			int buttonWidth = font.width(name) + 10;
			if(x + buttonWidth > contentLeft + contentWidth)
				break;
			Button button = Button.builder(Component.literal(name), b -> invitePlayer(name))
					.bounds(x, suggestionRowY, buttonWidth, SUGGESTION_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_invite_suggestion_tooltip", name)))
					.build();
			suggestionButtons.add(addRenderableWidget(button));
			x += buttonWidth + ROW_GAP;
		}
	}

	private void refreshRows() {
		if(list == null)
			return;
		List<PartyRow> rows = computeRows();
		if(rows.equals(displayedRows))
			return;
		displayedRows = rows;
		list.setRows(rows);
	}

	/**
	 * Updates the state of the widgets that depends on what has been typed or on the party data.
	 */
	private void updateWidgets() {
		if(createBox != null) {
			boolean allowed = isCreateAllowed();
			createBox.setTextColor(allowed ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
			createButton.active = allowed;
			createButton.setTooltip(allowed ? null : NAME_INVALID);
		}
		if(renameBox != null) {
			String name = getRenameValue();
			boolean allowed = PartyRules.isValidPartyName(name);
			renameBox.setTextColor(allowed ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
			renameButton.active = allowed && isRenameAllowed(name);
			renameButton.setTooltip(allowed ? null : NAME_INVALID);
		}
		if(inviteBox != null) {
			boolean allowed = isInviteNameAllowed();
			boolean empty = inviteText.isEmpty();
			inviteBox.setTextColor(allowed || empty ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
			inviteButton.active = allowed;
			inviteButton.setTooltip(allowed || empty ? null : PLAYER_NAME_INVALID);
		}
	}

	// ---- screen ----

	@Override
	public void tick() {
		super.tick();
		State currentState = computeState();
		if(currentState != state || !computeLayoutKey(currentState).equals(layoutKey)) {
			rebuildWidgets();
			return;
		}
		if(state == State.NO_PARTY && ++invitesRequestCounter >= INVITES_REQUEST_INTERVAL_TICKS)
			maybeRequestInvites();
		if(renameBox != null) {
			String currentName = getCurrentPartyName();
			if(renameText != null && renameText.trim().equals(currentName))
				renameText = null;
			if(renameText == null && !renameBox.isFocused() && !renameBox.getValue().equals(currentName))
				renameBox.setValue(currentName);
		}
		refreshRows();
		refreshSuggestions();
		updateWidgets();
	}

	private Component getInfoComponent(boolean measuring) {
		IClientPartyStorage<?, ?, ?> partyStorage = getPartyStorage();
		IClientParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = getPartyStorage().getParty();
		String ownerName = party == null ? "" : party.getOwner().getUsername();
		String memberNumbers = measuring ? "00 / 00" : partyStorage.getUIMemberCount() + " / " + partyStorage.getMemberLimit();
		String inviteNumbers = measuring ? "00 / 00" : partyStorage.getUIInviteCount() + " / " + partyStorage.getInviteLimit();
		Component spacing = Component.literal("   ");
		return Component.empty()
				.append(Component.translatable("gui.xaero_pac_ui_party_owner", Component.literal(ownerName).withStyle(s -> s.withColor(VALUE_COLOR))))
				.append(spacing)
				.append(Component.translatable("gui.xaero_pac_ui_party_member_count", Component.literal(memberNumbers).withStyle(s -> s.withColor(VALUE_COLOR))))
				.append(spacing)
				.append(Component.translatable("gui.xaero_pac_ui_party_invite_count", Component.literal(inviteNumbers).withStyle(s -> s.withColor(VALUE_COLOR))));
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		switch(state) {
			case NO_MOD -> guiGraphics.drawCenteredString(font, MainMenu.NO_HANDSHAKE, width / 2, messageY, 0xFFFF5555);
			case NO_PARTIES -> guiGraphics.drawCenteredString(font, MainMenu.NO_PARTIES, width / 2, messageY, 0xFFAAAAAA);
			case SYNCING -> guiGraphics.drawCenteredString(font, MainMenu.PARTY_SYNCING, width / 2, messageY, -1);
			case NO_PARTY -> {
				guiGraphics.drawString(font, CREATE_TITLE, contentLeft, createLabelY, -1);
				guiGraphics.drawString(font, INVITES_TITLE, contentLeft, invitesLabelY, -1);
			}
			case IN_PARTY -> renderPartyHeader(guiGraphics);
		}
	}

	private void renderPartyHeader(GuiGraphics guiGraphics) {
		if(renameBox == null) {
			String partyName = font.plainSubstrByWidth(getCurrentPartyName(), Math.max(0, contentWidth - 60));
			Component nameComponent = Component.translatable("gui.xaero_pac_ui_party_name", Component.literal(partyName).withStyle(s -> s.withColor(VALUE_COLOR)));
			guiGraphics.drawCenteredString(font, nameComponent, width / 2, messageY, -1);
		}
		int lineY = infoLineY;
		for(FormattedCharSequence line : font.split(getInfoComponent(false), contentWidth)) {
			guiGraphics.drawCenteredString(font, line, width / 2, lineY, -1);
			lineY += font.lineHeight + 2;
		}
	}

	private boolean isTextFieldFocused() {
		return createBox != null && createBox.isFocused() || inviteBox != null && inviteBox.isFocused() || renameBox != null && renameBox.isFocused();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		KeyMapping keyMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openPartyMenu;
		InputConstants.Key boundKey = Services.PLATFORM.getKeyBindingHelper().getBoundKey(keyMapping);
		if(!isTextFieldFocused() && boundKey.getType() == InputConstants.Type.KEYSYM && keyCode == boundKey.getValue()) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		KeyMapping keyMapping = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getKeyBindings().openPartyMenu;
		IKeyBindingHelper keyBindingHelper = Services.PLATFORM.getKeyBindingHelper();
		InputConstants.Key boundKey = keyBindingHelper.getBoundKey(keyMapping);
		if(!isTextFieldFocused() && boundKey.getType() == InputConstants.Type.MOUSE && button == boundKey.getValue()) {
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return getEscape() != null && getEscape().isPauseScreen();
	}

}
