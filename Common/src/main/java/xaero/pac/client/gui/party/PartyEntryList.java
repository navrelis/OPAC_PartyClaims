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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The scrollable list of the party screen. It shows the members and the sent invites of the party of the local player
 * or the invites that the local player has received, depending on the rows that it is given.
 */
public class PartyEntryList extends ContainerObjectSelectionList<PartyEntryList.PartyEntry> {

	public static final int ITEM_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_PADDING = 12;
	private static final int GAP = 2;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int OWNER_NAME_COLOR = 0xFFFFAA00;
	private static final int SUBTEXT_COLOR = 0xFFAAAAAA;

	private static final Component KICK = Component.translatable("gui.xaero_pac_party_screen_kick");
	private static final Component MAKE_OWNER = Component.translatable("gui.xaero_pac_party_screen_make_owner");
	private static final Component CANCEL_INVITE = Component.translatable("gui.xaero_pac_party_screen_cancel_invite");
	private static final Component ACCEPT = Component.translatable("gui.xaero_pac_party_screen_accept");
	private static final Component DECLINE = Component.translatable("gui.xaero_pac_party_screen_decline");

	private final Actions actions;
	private final Font font;
	private int rowWidth;
	private int rankWidth;
	private int kickWidth;
	private int makeOwnerWidth;
	private int cancelInviteWidth;
	private int acceptWidth;
	private int declineWidth;
	private boolean showKickSlot;
	private boolean showMakeOwnerSlot;

	public PartyEntryList(Minecraft minecraft, int width, int height, int y, int rowWidth, Actions actions) {
		super(minecraft, width, height, y, ITEM_HEIGHT);
		this.actions = actions;
		this.font = minecraft.font;
		this.rowWidth = rowWidth;
		int widestRank = 0;
		for(PartyMemberRank rank : PartyMemberRank.values())
			widestRank = Math.max(widestRank, font.width(getRankName(rank, false)));
		widestRank = Math.max(widestRank, font.width(getRankName(PartyMemberRank.ADMIN, true)));
		rankWidth = widestRank + BUTTON_PADDING;
		kickWidth = getButtonWidth(KICK);
		makeOwnerWidth = getButtonWidth(MAKE_OWNER);
		cancelInviteWidth = getButtonWidth(CANCEL_INVITE);
		acceptWidth = getButtonWidth(ACCEPT);
		declineWidth = getButtonWidth(DECLINE);
	}

	private int getButtonWidth(Component label) {
		return font.width(label) + BUTTON_PADDING;
	}

	public static Component getRankName(PartyMemberRank rank, boolean owner) {
		String rankKey = owner ? "owner" : rank.name().toLowerCase(Locale.ROOT);
		return Component.translatable("gui.xaero_pac_party_screen_rank_" + rankKey);
	}

	@Override
	public int getRowWidth() {
		return rowWidth;
	}

	public void setRowWidth(int rowWidth) {
		this.rowWidth = rowWidth;
	}

	/**
	 * Replaces the displayed rows, keeping the scroll position as far as possible.
	 */
	public void setRows(List<PartyRow> rows) {
		showKickSlot = false;
		showMakeOwnerSlot = false;
		for(PartyRow row : rows) {
			if(row instanceof PartyRow.Member member) {
				showKickSlot |= member.canKick();
				showMakeOwnerSlot |= member.canTransfer();
			}
		}
		List<PartyEntry> entries = new ArrayList<>(rows.size());
		for(PartyRow row : rows)
			entries.add(createEntry(row));
		replaceEntries(entries);
		clampScrollAmount();
	}

	private PartyEntry createEntry(PartyRow row) {
		if(row instanceof PartyRow.Member member)
			return new MemberEntry(member);
		if(row instanceof PartyRow.SentInvite invite)
			return new SentInviteEntry(invite);
		if(row instanceof PartyRow.ReceivedInvite invite)
			return new ReceivedInviteEntry(invite);
		if(row instanceof PartyRow.Header header)
			return new TextEntry(header.title(), false);
		return new TextEntry(((PartyRow.Message) row).text(), true);
	}

	private String clipToWidth(String text, int maxWidth) {
		if(maxWidth <= 0)
			return "";
		if(font.width(text) <= maxWidth)
			return text;
		String ellipsis = "...";
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
	}

	private int centerButtonY(int top) {
		return top + (ITEM_HEIGHT - 4 - BUTTON_HEIGHT) / 2;
	}

	public interface Actions {

		void onRankClicked(PartyRow.Member member);

		void onKickClicked(PartyRow.Member member);

		void onMakeOwnerClicked(PartyRow.Member member);

		void onCancelInviteClicked(PartyRow.SentInvite invite);

		void onAcceptClicked(PartyRow.ReceivedInvite invite);

		void onDeclineClicked(PartyRow.ReceivedInvite invite);

	}

	public abstract static class PartyEntry extends ContainerObjectSelectionList.Entry<PartyEntry> {

		protected final List<AbstractWidget> widgets = new ArrayList<>();

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
		}

	}

	private class MemberEntry extends PartyEntry {

		private final PartyRow.Member member;
		private final Button rankButton;
		private final Button kickButton;
		private final Button makeOwnerButton;

		private MemberEntry(PartyRow.Member member) {
			this.member = member;
			Component rankName = getRankName(member.rank(), member.owner());
			if(member.nextRank() != null) {
				Component nextRankName = getRankName(member.nextRank(), false);
				rankButton = Button.builder(rankName, b -> actions.onRankClicked(member))
						.size(rankWidth, BUTTON_HEIGHT)
						.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_rank_tooltip", member.name(), nextRankName)))
						.build();
				widgets.add(rankButton);
			} else
				rankButton = null;
			if(member.canKick()) {
				kickButton = Button.builder(KICK, b -> actions.onKickClicked(member))
						.size(kickWidth, BUTTON_HEIGHT)
						.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_kick_tooltip", member.name())))
						.build();
				widgets.add(kickButton);
			} else
				kickButton = null;
			if(member.canTransfer()) {
				makeOwnerButton = Button.builder(MAKE_OWNER, b -> actions.onMakeOwnerClicked(member))
						.size(makeOwnerWidth, BUTTON_HEIGHT)
						.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_make_owner_tooltip", member.name())))
						.build();
				widgets.add(makeOwnerButton);
			} else
				makeOwnerButton = null;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int buttonY = centerButtonY(top);
			int right = left + width - GAP;
			int makeOwnerSlotLeft = right;
			if(showMakeOwnerSlot) {
				makeOwnerSlotLeft = right - makeOwnerWidth;
				if(makeOwnerButton != null) {
					makeOwnerButton.setPosition(makeOwnerSlotLeft, buttonY);
					makeOwnerButton.render(guiGraphics, mouseX, mouseY, partialTick);
				}
				makeOwnerSlotLeft -= GAP;
			}
			int kickSlotLeft = makeOwnerSlotLeft;
			if(showKickSlot) {
				kickSlotLeft = makeOwnerSlotLeft - kickWidth;
				if(kickButton != null) {
					kickButton.setPosition(kickSlotLeft, buttonY);
					kickButton.render(guiGraphics, mouseX, mouseY, partialTick);
				}
				kickSlotLeft -= GAP;
			}
			int rankLeft = kickSlotLeft - rankWidth;
			if(rankButton != null) {
				rankButton.setPosition(rankLeft, buttonY);
				rankButton.render(guiGraphics, mouseX, mouseY, partialTick);
			} else {
				Component rankName = getRankName(member.rank(), member.owner()).copy().withStyle(member.rank().getColor());
				guiGraphics.drawString(font, rankName, rankLeft + (rankWidth - font.width(rankName)) / 2, top + (ITEM_HEIGHT - 4 - font.lineHeight) / 2 + 1, -1);
			}
			Component nameComponent = Component.literal(clipToWidth(member.name(), rankLeft - left - 8));
			if(member.self())
				nameComponent = nameComponent.copy().withStyle(ChatFormatting.BOLD);
			int nameColor = member.owner() ? OWNER_NAME_COLOR : NAME_COLOR;
			guiGraphics.drawString(font, nameComponent, left + 4, top + (ITEM_HEIGHT - 4 - font.lineHeight) / 2 + 1, nameColor);
		}

	}

	private class SentInviteEntry extends PartyEntry {

		private final PartyRow.SentInvite invite;
		private final Button cancelButton;

		private SentInviteEntry(PartyRow.SentInvite invite) {
			this.invite = invite;
			if(invite.canCancel()) {
				cancelButton = Button.builder(CANCEL_INVITE, b -> actions.onCancelInviteClicked(invite))
						.size(cancelInviteWidth, BUTTON_HEIGHT)
						.tooltip(Tooltip.create(Component.translatable("gui.xaero_pac_party_screen_cancel_invite_tooltip", invite.name())))
						.build();
				widgets.add(cancelButton);
			} else
				cancelButton = null;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int textLeftLimit = left + width - GAP;
			if(cancelButton != null) {
				cancelButton.setPosition(left + width - GAP - cancelInviteWidth, centerButtonY(top));
				cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
				textLeftLimit = cancelButton.getX() - 8;
			}
			String name = clipToWidth(invite.name(), textLeftLimit - left - 4);
			guiGraphics.drawString(font, name, left + 4, top + (ITEM_HEIGHT - 4 - font.lineHeight) / 2 + 1, SUBTEXT_COLOR);
		}

	}

	private class ReceivedInviteEntry extends PartyEntry {

		private final PartyRow.ReceivedInvite invite;
		private final Button acceptButton;
		private final Button declineButton;

		private ReceivedInviteEntry(PartyRow.ReceivedInvite invite) {
			this.invite = invite;
			acceptButton = Button.builder(ACCEPT, b -> actions.onAcceptClicked(invite)).size(acceptWidth, BUTTON_HEIGHT).build();
			declineButton = Button.builder(DECLINE, b -> actions.onDeclineClicked(invite)).size(declineWidth, BUTTON_HEIGHT).build();
			widgets.add(acceptButton);
			widgets.add(declineButton);
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int buttonY = centerButtonY(top);
			declineButton.setPosition(left + width - GAP - declineWidth, buttonY);
			acceptButton.setPosition(declineButton.getX() - GAP - acceptWidth, buttonY);
			declineButton.render(guiGraphics, mouseX, mouseY, partialTick);
			acceptButton.render(guiGraphics, mouseX, mouseY, partialTick);
			int textWidth = acceptButton.getX() - left - 8;
			guiGraphics.drawString(font, clipToWidth(invite.partyName(), textWidth), left + 4, top + 1, NAME_COLOR);
			Component ownerLine = Component.translatable("gui.xaero_pac_party_screen_invite_from", invite.ownerName());
			guiGraphics.drawString(font, clipToWidth(ownerLine.getString(), textWidth), left + 4, top + 1 + font.lineHeight + 1, SUBTEXT_COLOR);
		}

	}

	private class TextEntry extends PartyEntry {

		private final Component text;
		private final boolean centered;

		private TextEntry(Component text, boolean centered) {
			this.text = text;
			this.centered = centered;
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int textY = top + (ITEM_HEIGHT - 4 - font.lineHeight) / 2 + 1;
			if(centered)
				guiGraphics.drawCenteredString(font, text, left + width / 2, textY, SUBTEXT_COLOR);
			else
				guiGraphics.drawString(font, text, left + 4, textY, OWNER_NAME_COLOR);
		}

	}

}
