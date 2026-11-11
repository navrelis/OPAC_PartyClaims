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

package xaero.pac.client.gui;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.client.gui.configview.ConfigOptionGrouping;
import xaero.pac.client.gui.configview.ConfigResetRule;
import xaero.pac.client.gui.configview.ConfigViewLayout;
import xaero.pac.client.gui.configview.ConfigViewState;
import xaero.pac.client.gui.group.PlayerGroupsScreen;
import xaero.pac.client.gui.widget.value.BooleanValueHolder;
import xaero.pac.client.player.config.IPlayerConfigClientStorageManager;
import xaero.pac.client.player.config.PlayerConfigClientStorage;
import xaero.pac.client.player.config.PlayerConfigStringableOptionClientStorage;
import xaero.pac.client.player.config.sub.PlayerSubConfigClientStorage;
import xaero.pac.common.misc.ListFactory;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.PlayerConfigHexOptionSpec;
import xaero.pac.common.server.player.config.PlayerConfigListIterationOptionSpec;
import xaero.pac.common.server.player.config.PlayerConfigStringOptionSpec;
import xaero.pac.common.server.player.config.api.PlayerConfigType;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.*;
import java.util.stream.Stream;

public final class PlayerConfigScreen extends WidgetListScreen {

	private static final Object NULL_PLACEHOLDER = new Comparable<Object>() {
		@Override
		public int compareTo(@Nonnull Object o) {
			return 0;
		}
	};
	public static final Component SYNCING_IN_PROGRESS = Component.translatable("gui.xaero_pac_ui_player_config_syncing");
	public static final Component BEING_DELETED = Component.translatable("gui.xaero_pac_ui_player_config_being_deleted");
	private final BiConsumer<PlayerConfigScreen, Button> refreshHandler;
	private Button refreshButton;
	private Button playerGroupsButton;
	private final PlayerConfigClientStorage data;
	private final PlayerConfigClientStorage optionValueSourceData;
	private final boolean shouldWaitForData;
	private final boolean beingDeletedStateOnOpen;
	private final Component mainTitle;
	private final String otherPlayerName;
	//[Team Claims] the option rows are grouped, collapsed and filtered into the displayed elements
	private final List<WidgetListElement<?>> pinnedElements;
	private final List<OptionRow> optionRows;
	private final List<ConfigViewLayout.Entry> optionEntries;
	private final ConfigViewState viewState;
	private String searchQuery = "";
	private boolean noSearchResults;
	private EditBox searchBox;

	private record OptionRow(ConfigViewLayout.Entry entry, ConfigOptionRowListElement element) {
	}

	private PlayerConfigScreen(
			List<WidgetListElement<?>> elements,
			List<OptionRow> optionRows,
			ConfigViewState viewState,
			List<EditBox> tickableBoxes,
			BiConsumer<PlayerConfigScreen, Button> refreshHandler,
			Screen escape,
			Screen parent,
			Component title,
			PlayerConfigClientStorage data,
			PlayerConfigClientStorage optionValueSourceData,
			boolean shouldWaitForData,
			boolean beingDeletedStateOnOpen,
			Component mainTitle,
			String otherPlayerName
	) {
		super(elements, tickableBoxes, escape, parent, title);
		this.refreshHandler = refreshHandler;
		this.data = data;
		this.optionValueSourceData = optionValueSourceData;
		this.shouldWaitForData = shouldWaitForData;
		this.beingDeletedStateOnOpen = beingDeletedStateOnOpen;
		this.mainTitle = mainTitle;
		this.otherPlayerName = otherPlayerName;
		this.pinnedElements = new ArrayList<>(elements);
		this.optionRows = optionRows;
		this.optionEntries = new ArrayList<>();
		for(OptionRow row : optionRows)
			optionEntries.add(row.entry());
		this.viewState = viewState;
		rebuildElements();
	}

	//[Team Claims] the displayed elements: the sub-config controls, then the headers and the options that are shown
	private void rebuildElements(){
		elements.clear();
		elements.addAll(pinnedElements);
		List<ConfigViewLayout.Row> rows = ConfigViewLayout.compute(optionEntries, viewState, searchQuery, pinnedElements.size());
		boolean searching = !ConfigViewLayout.searchTerms(searchQuery).isEmpty();
		noSearchResults = searching && rows.isEmpty();
		int indexInGroup = 0;
		for(ConfigViewLayout.Row row : rows) {
			switch(row.kind()) {
				case HEADER -> {
					indexInGroup = 0;
					elements.add(createGroupHeader(row, searching));
				}
				case OPTION -> {
					ConfigOptionRowListElement element = optionRows.get(row.entryIndex()).element();
					element.setOdd(indexInGroup++ % 2 == 1);
					elements.add(element);
				}
				case FILLER -> elements.add(createFiller());
			}
		}
	}

	private WidgetListElement<?> createGroupHeader(ConfigViewLayout.Row row, boolean searching){
		String groupKey = row.groupKey();
		String translationKey = "gui.xaero_pac_config_group_" + groupKey;
		Component title = I18n.exists(translationKey) ? Component.translatable(translationKey) : Component.literal(ConfigOptionGrouping.fallbackTitle(groupKey));
		Component text = Component.literal(row.expanded() ? "▾ " : "▸ ").append(title).append(" (" + row.shown() + ")").withStyle(ChatFormatting.GOLD);
		return SimpleWidgetListElement.Builder.begin()
				.setW(ConfigOptionRowListElement.WIDTH)
				.setH(20)
				.setMutable(!searching)//while searching, the matching groups are open
				.setWidgetSupplier((el, xy) -> Button.builder(text, b -> {
					viewState.toggle(groupKey);
					relayout();
				}).bounds(xy.getX(), xy.getY(), ConfigOptionRowListElement.WIDTH, 20).build())
				.build();
	}

	//keeps a header from being the last row of a column
	private WidgetListElement<?> createFiller(){
		return SimpleWidgetListElement.Builder.begin()
				.setW(ConfigOptionRowListElement.WIDTH)
				.setH(20)
				.setMutable(false)
				.setWidgetSupplier((el, xy) -> {
					Button filler = Button.builder(Component.empty(), b -> {}).bounds(xy.getX(), xy.getY(), 1, 1).build();
					filler.visible = false;
					return filler;
				})
				.build();
	}

	private void relayout(){
		rebuildElements();
		init(minecraft, width, height);
	}

	private void onSearchChanged(String value){
		if(value.equals(searchQuery))
			return;
		boolean refocus = searchBox.isFocused();
		searchQuery = value;
		setPage(0);
		relayout();
		if(refocus)
			setFocused(searchBox);
	}

	@Override
	protected void init() {
		super.init();
		int xAnchor = width / 2;
		int topButtonsWidth = 84;
		addRenderableWidget(refreshButton = Button.builder(
						Component.translatable("gui.xaero_pac_ui_player_config_refresh"),
						b -> refreshHandler.accept(this, b)
				).bounds(xAnchor - 205, 5, topButtonsWidth, 20).build()
		);
		if(!optionRows.isEmpty()) {
			//[Team Claims] below the list between the page buttons; it is only focused by a click
			if(searchBox == null) {
				searchBox = new EditBox(font, 0, 0, ConfigOptionRowListElement.WIDTH, 20, Component.translatable("gui.xaero_pac_config_search"));
				searchBox.setHint(Component.translatable("gui.xaero_pac_config_search_hint"));
				searchBox.setMaxLength(64);
				searchBox.setResponder(this::onSearchChanged);
			}
			searchBox.setX(xAnchor - ConfigOptionRowListElement.WIDTH / 2);
			searchBox.setY(height / 7 + 6 * ROW_HEIGHT);
			searchBox.setTextColor(noSearchResults ? 0xFFFF5555 : 14737632);
			addRenderableWidget(searchBox);
		}
		if(data.isSyncInProgress())
			return;
		addRenderableWidget(playerGroupsButton = Button.builder(
						Component.translatable("gui.xaero_pac_ui_player_config_player_groups"),
						b -> openGroupsScreen()
				).bounds(xAnchor + 205 - topButtonsWidth, 5, topButtonsWidth, 20).build()
		);
	}

	public void openGroupsScreen(){
		minecraft.setScreen(
				PlayerGroupsScreen.Builder
						.begin()
						.setConfigData(data)
						.setEscape(escape)
						.setParent(this)
						.setConfigTitle(mainTitle)
						.build()
		);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.render(guiGraphics, mouseX, mouseY, partial);
	}

	@Override
	protected void renderPreDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
		super.renderPreDropdown(guiGraphics, mouseX, mouseY, partial);
		if(shouldWaitForData){
			if(!data.isSyncInProgress())
				refresh();
			else
				guiGraphics.drawCenteredString(font, SYNCING_IN_PROGRESS, width / 2, height / 7 + 64, -1);
		}
		if(beingDeletedStateOnOpen != optionValueSourceData.isBeingDeleted())
			refresh();
		else if(optionValueSourceData.isBeingDeleted())
			guiGraphics.drawCenteredString(font, BEING_DELETED, width / 2, height / 7 + 124, -1);
	}

	public static MutableComponent getUICommentForOption(IPlayerConfigOptionSpecAPI<?> option){
		String commentTranslated = I18n.get(option.getCommentTranslation(), (Object[]) option.getCommentTranslationArgs());
		if(commentTranslated.equals("default"))
			commentTranslated = option.getComment();
		if(option.getTooltipPrefix() != null)
			commentTranslated = option.getTooltipPrefix() + "\n" + commentTranslated;
		return Component.literal(commentTranslated);
	}

	public String getOtherPlayerName() {
		return otherPlayerName;
	}

	public void refresh(){
		refreshButton.onPress();
	}

	public final static class Builder {
		
		private final ListFactory listFactory;
		private IPlayerConfigClientStorageManager<?> manager;
		private PlayerConfigClientStorage data;
		private PlayerConfigClientStorage defaultPlayerConfigData;
		private PlayerConfigClientStorage mainPlayerConfigData;
		private Screen escape;
		private Screen parent;
		private Component title;
		private String otherPlayerName;
		
		private Builder(ListFactory listFactory) {
			super();
			this.listFactory = listFactory;
		}

		public Builder setDefault() {
			setManager(null);
			setEscape(null);
			setParent(null);
			setData(null);
			setTitle(null);
			setMainPlayerConfigData(null);
			return this;
		}

		public Builder setManager(IPlayerConfigClientStorageManager<?> manager) {
			this.manager = manager;
			return this;
		}

		public Builder setData(PlayerConfigClientStorage data) {
			this.data = data;
			return this;
		}
		
		public Builder setDefaultPlayerConfigData(PlayerConfigClientStorage defaultPlayerConfigData) {
			this.defaultPlayerConfigData = defaultPlayerConfigData;
			return this;
		}

		public Builder setMainPlayerConfigData(PlayerConfigClientStorage mainPlayerConfigData) {
			this.mainPlayerConfigData = mainPlayerConfigData;
			return this;
		}

		public Builder setEscape(Screen escape) {
			this.escape = escape;
			return this;
		}
		
		public Builder setParent(Screen parent) {
			this.parent = parent;
			return this;
		}
		
		public Builder setTitle(Component title) {
			this.title = title;
			return this;
		}
		
		public Builder setOtherPlayerName(String otherPlayerName) {
			this.otherPlayerName = otherPlayerName;
			return this;
		}

		private <T> T getOptionValue(PlayerConfigStringableOptionClientStorage<T> option){
			return getOptionValue(data, defaultPlayerConfigData, option);
		}

		private static <T> T getOptionValue(
				PlayerConfigClientStorage data,
				PlayerConfigClientStorage defaultPlayerConfigData,
				PlayerConfigStringableOptionClientStorage<T> option){
			T value;
			if(option.isDefaulted())
				value = defaultPlayerConfigData.getOption(option.getOption()).getValue();
			else
				value = option.getValue();
			return value;
		}

		private <HT, T> CycleButton.OnValueChange<HT> getRegularValueChangeListener(SimpleValueWidgetListElement.Final<T> el, PlayerConfigStringableOptionClientStorage<T> option, Function<HT, T> holderToValue, PlayerConfigClientStorage data) {
			return (b, vh) -> {
				if(!el.mutable)
					return;
				//on value change
				T v = holderToValue.apply(vh);
				if(v == NULL_PLACEHOLDER)
					v = null;
				el.setDraftValue(v);
				option.setCastValue(v);
				OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().syncToServer(data, option);
			};
		}

		private <HT, T> BiFunction<SimpleValueWidgetListElement.Final<T>, Vec3i, AbstractWidget> getIterationWidgetSupplierForValues(Supplier<List<HT>> valuesSupplier, PlayerConfigStringableOptionClientStorage<T> option, int elementWidth, int elementHeight, Component optionTitle, Function<T, HT> valueToHolder, Function<HT, T> holderToValue, PlayerConfigClientStorage data){
			final PlayerConfigClientStorage finalDefaultPlayerConfigData = defaultPlayerConfigData;
			return (el, xy) -> CycleButton.<HT>builder(v -> {
						Component defaultDisplay = option.getOption().getValueDisplayName(holderToValue.apply(v));
						if(option.getType() == Integer.class){
							String translationKey = option.getTranslation() + "_" + defaultDisplay.getString();
							String translatedText = I18n.get(translationKey);
							if(!translatedText.equals("default") && !translatedText.equals(translationKey))
								return Component.translatable(translationKey).setStyle(defaultDisplay.getStyle());
						}
						return defaultDisplay;
					})
					.withValues(valuesSupplier.get())
					.withInitialValue(valueToHolder.apply(getOptionValue(data, finalDefaultPlayerConfigData, option)))
					.create(xy.getX(), xy.getY(), elementWidth, elementHeight, optionTitle, getRegularValueChangeListener(el, option, holderToValue, data));
		}

		private <T> BiFunction<SimpleValueWidgetListElement.Final<T>, Vec3i, AbstractWidget> getIterationWidgetSupplier(PlayerConfigStringableOptionClientStorage<T> option, int elementWidth, int elementHeight, Component optionTitle, T currentValue, PlayerConfigClientStorage data){
			PlayerConfigClientStorage valueSourceConfig;
			if(option.isDefaulted() && data.getType() == PlayerConfigType.PLAYER)
				valueSourceConfig = defaultPlayerConfigData;
			else
				valueSourceConfig = data;
			@SuppressWarnings("unchecked")
			T nullPlaceholder = (T) NULL_PLACEHOLDER;
			Supplier<List<T>> valuesSupplier = () -> {
				List<T> values;
				if (option.getOption() instanceof PlayerConfigListIterationOptionSpec<T> listIterationOptionSpec) {
					values = listIterationOptionSpec.getClientSideListGetter().apply(valueSourceConfig);
					if (values == null)
						values = Lists.newArrayList(currentValue);
					else
						values = Lists.newArrayList(values);
				} else
					values = Lists.newArrayList(currentValue);
				if (data instanceof PlayerSubConfigClientStorage) {
					values.add(0, nullPlaceholder);
				}
				return values;
			};
			return getIterationWidgetSupplierForValues(valuesSupplier, option, elementWidth, elementHeight, optionTitle, v -> v == null ? nullPlaceholder : v, h -> h == NULL_PLACEHOLDER ? null : h, data);
		}

		private BiFunction<SimpleValueWidgetListElement.Final<Boolean>, Vec3i, AbstractWidget> getOnOffWidgetSupplier(PlayerConfigStringableOptionClientStorage<Boolean> option, int elementWidth, int elementHeight, Component optionTitle, BooleanValueHolder currentValue, PlayerConfigClientStorage data){
			List<BooleanValueHolder> values = Lists.newArrayList(BooleanValueHolder.FALSE, BooleanValueHolder.TRUE);
			if(data instanceof PlayerSubConfigClientStorage)
				values.add(0, BooleanValueHolder.NULL);
			return getIterationWidgetSupplierForValues(() -> values, option, elementWidth, elementHeight, optionTitle, BooleanValueHolder::of, BooleanValueHolder::getValue, data);
		}

		private <T> SimpleValueWidgetListElement<T, ?> createIterationWidgetListElement(
				PlayerConfigStringableOptionClientStorage<T> option, int elementWidth, int elementHeight,
				Component optionTitle, List<FormattedCharSequence> tooltip, PlayerConfigClientStorage data){
			T value = getOptionValue(option);
			BiFunction<SimpleValueWidgetListElement.Final<T>, Vec3i, AbstractWidget> widgetSupplier = getIterationWidgetSupplier(option, elementWidth, elementHeight, optionTitle, value, data);
			return SimpleValueWidgetListElement.FinalBuilder.<T>begin()
					.setW(elementWidth)
					.setH(elementHeight)
					.setWidgetSupplier(widgetSupplier)
					.setTooltip(tooltip)
					.setMutable(isMutable(data, option))
					.setStartValue(value)
					.build();
		}

		private SimpleValueWidgetListElement<Boolean, ?> createOnOffWidgetListElement(
				PlayerConfigStringableOptionClientStorage<Boolean> option, int elementWidth, int elementHeight,
				Component optionTitle, List<FormattedCharSequence> tooltip, PlayerConfigClientStorage data){
			Boolean value = getOptionValue(option);
			BiFunction<SimpleValueWidgetListElement.Final<Boolean>, Vec3i, AbstractWidget> widgetSupplier = getOnOffWidgetSupplier(option, elementWidth, elementHeight, optionTitle, BooleanValueHolder.of(value), data);
			return SimpleValueWidgetListElement.FinalBuilder.<Boolean>begin()
					.setW(elementWidth)
					.setH(elementHeight)
					.setWidgetSupplier(widgetSupplier)
					.setTooltip(tooltip)
					.setMutable(isMutable(data, option))
					.setStartValue(value)
					.build();
		}

		private WidgetListElement<?> createSubConfigWidgetListElement(int elementWidth, int elementHeight, List<String> subConfigs, int indexOfSelected){
			List<FormattedCharSequence> tooltip = Minecraft.getInstance().font.split(Component.translatable("gui.xaero_pac_ui_sub_config_dropdown_tooltip"), 200);
			return DropdownWidgetListElement.Builder.<String>begin()
					.setW(elementWidth)
					.setH(elementHeight)
					.setTooltip(tooltip)
					.setMutable(true)
					.setOptions(subConfigs)
					.setStartIndex(indexOfSelected)
					.setTitle(Component.translatable("gui.xaero_pac_ui_sub_config_dropdown"))
					.setValueChangeConsumer(v-> {
						data.setSelectedSubConfig(v);
						PlayerConfigScreen recreatedScreen = build();
						Minecraft.getInstance().setScreen(recreatedScreen);
						recreatedScreen.setFocused(recreatedScreen.children().get(0));//works while the sub-config menu is the first element
					})
					.build();
		}

		private void addSubConfigControls(List<WidgetListElement<?>> elements, int elementWidth, int elementHeight){
			Minecraft minecraft = Minecraft.getInstance();
			List<String> subConfigs = data.getSubConfigIds();
			if(subConfigs.isEmpty())
				throw new IllegalStateException();
			PlayerConfigClientStorage usedSubConfigSyncDest;
			PlayerConfigStringableOptionClientStorage<String> usedSubConfigOptionStorage;
			if(data.getType() == PlayerConfigType.SERVER){
				usedSubConfigSyncDest = mainPlayerConfigData;
				usedSubConfigOptionStorage = mainPlayerConfigData.getOption(PlayerConfigOptions.USED_SERVER_SUBCLAIM);
			} else if(data.getType() == PlayerConfigType.PARTY_CLAIMS){
				usedSubConfigSyncDest = mainPlayerConfigData;
				usedSubConfigOptionStorage = mainPlayerConfigData.getOption(PlayerConfigOptions.USED_PARTY_SUBCLAIM);
			} else {
				usedSubConfigSyncDest = data;
				usedSubConfigOptionStorage = data.getOption(PlayerConfigOptions.USED_SUBCLAIM);
			}

			String selected = data.getSelectedSubConfig();
			int indexOfSelectedSub = -1;
			if(selected != null)
				indexOfSelectedSub = subConfigs.indexOf(selected);
			if(indexOfSelectedSub < 0)
				indexOfSelectedSub = subConfigs.indexOf(usedSubConfigOptionStorage.getValue());
			if(indexOfSelectedSub < 0)
				indexOfSelectedSub = subConfigs.indexOf(PlayerConfig.MAIN_SUB_ID);
			if(indexOfSelectedSub < 0)
				indexOfSelectedSub = 0;
			data.setSelectedSubConfig(subConfigs.get(indexOfSelectedSub));
			PlayerConfigClientStorage subData = data.isSubConfigSelected() ?
					data.getOrCreateSubConfig(data.getSelectedSubConfig()) : data;

			elements.add(createSubConfigWidgetListElement(elementWidth, elementHeight, subConfigs, indexOfSelectedSub));
			boolean canCreateSubs = data.getPermissions().canEdit();

			if(!data.getType().hasDimensionSubConfigs()){
				boolean isCurrentlyUsed = Objects.equals(usedSubConfigOptionStorage.getValue(), data.getSelectedSubConfig());
				WidgetListElement<?> useSubConfigButtonWidget = SimpleWidgetListElement.Builder.begin()
						.setW(elementWidth)
						.setH(elementHeight)
						.setMutable(!isCurrentlyUsed && !subData.isBeingDeleted())
						.setTooltip(minecraft.font.split(Component.translatable(isCurrentlyUsed? "gui.xaero_pac_ui_sub_config_use_button_used_tooltip" : "gui.xaero_pac_ui_sub_config_use_button_tooltip"), 200))
						.setWidgetSupplier((el, xy) -> Button.builder(isCurrentlyUsed ? Component.translatable("gui.xaero_pac_ui_sub_config_use_button_used") : Component.translatable("gui.xaero_pac_ui_sub_config_use_button", data.getSelectedSubConfig()),
								b -> {
									usedSubConfigOptionStorage.setValue(data.getSelectedSubConfig());
									OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().syncToServer(usedSubConfigSyncDest, usedSubConfigOptionStorage);
									minecraft.setScreen(build());
								}).bounds(xy.getX(), xy.getY(), elementWidth, elementHeight).build()).build();
				elements.add(useSubConfigButtonWidget);
			}

			// [Team Claims] a team sub-config is managed by the party, it can't be deleted manually
			boolean isTeamSubConfig = xaero.pac.common.server.claims.TeamClaimsIntegration.isTeamSubId(data.getSelectedSubConfig());
			WidgetListElement<?> deleteSubConfigButtonWidget = SimpleWidgetListElement.Builder.begin()
					.setW(elementWidth)
					.setH(elementHeight)
					.setMutable(canCreateSubs && data.isSubConfigSelected() && !subData.isBeingDeleted() && !isTeamSubConfig)
					.setWidgetSupplier((el, xy) -> Button.builder(Component.translatable("gui.xaero_pac_ui_sub_config_delete_button", data.getSelectedSubConfig()),
							b -> {
								String s = data.getSelectedSubConfig();
								minecraft.setScreen(new ConfirmScreen(result -> {
									if(result) {
										data.getOrCreateSubConfig(s).setBeingDeleted(true);
										OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().requestDeleteSubConfig(data, s);
									}
									minecraft.setScreen(build());
								}, Component.translatable("gui.xaero_pac_ui_sub_config_delete_button_confirm1", s),
										Component.translatable("gui.xaero_pac_ui_sub_config_delete_button_confirm2")));
							}).bounds(xy.getX(), xy.getY(), elementWidth, elementHeight).build()).build();
			elements.add(deleteSubConfigButtonWidget);

			Component subIdRulesComponent = data.getType().hasDimensionSubConfigs() ?
					Component.translatable("gui.xaero_pac_config_create_sub_id_dimension_rules") :
					Component.translatable("gui.xaero_pac_config_create_sub_id_rules", PlayerConfig.MAX_SUB_ID_LENGTH);
			int maxSubIdLength = data.getType().hasDimensionSubConfigs() ? 500 : PlayerConfig.MAX_SUB_ID_LENGTH;
			WidgetListElement<?> createSubConfigWidget = TextWidgetListElement.Builder.begin()
					.setW(elementWidth)
					.setH(elementHeight)
					.setTitle(Component.translatable("gui.xaero_pac_ui_sub_config_create_widget"))
					.setTooltip(minecraft.font.split(Component.translatable("gui.xaero_pac_ui_sub_config_create_widget_tooltip", subIdRulesComponent), 200))
					.setMutable(canCreateSubs && data.getSubCount() < data.getSubConfigLimit())
					.setStartValue("")
					.setFilter(Objects::nonNull)
					.setValidator(s ->
							data.getType().hasDimensionSubConfigs() ? PlayerConfig.isValidDimensionSubId(s) :
									PlayerConfig.isValidSubId(s) && !usedSubConfigOptionStorage.getValidator().test(data, s)
					)
					.setResponder((el, s) -> {
						data.setSyncInProgress(true);
						data.setSelectedSubConfig(s);
						OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().requestCreateSubConfig(data, s);
						minecraft.setScreen(build());
					})
					.setMaxLength(maxSubIdLength)
					.setBoxWidth(112)
					.build();
			elements.add(createSubConfigWidget);
		}

		// [Team Claims] a row with the reset button; reset sets the value the same way a change of the value does
		private <T> OptionRow createRow(PlayerConfigStringableOptionClientStorage<T> option, WidgetListElement<?> valueElement, PlayerConfigClientStorage source, boolean mutable, Consumer<T> reset){
			boolean subConfig = source instanceof PlayerSubConfigClientStorage;
			T serverDefault = null;
			if(!subConfig && defaultPlayerConfigData != null && (source.getType() == PlayerConfigType.PLAYER || source.getType() == PlayerConfigType.PARTY_CLAIMS))
				serverDefault = defaultPlayerConfigData.getOption(option.getOption()).getValue();
			T target = ConfigResetRule.target(subConfig, serverDefault, option.getOption().getDefaultValue());
			ConfigOptionRowListElement row = new ConfigOptionRowListElement(
					valueElement.h,
					valueElement,
					() -> ConfigResetRule.isVisible(mutable, option.getValue(), target),
					() -> reset.accept(target)
			);
			String name = Component.translatable(option.getTranslation(), option.getTranslationArgs()).getString();
			String shortenedId = option.getOption().getShortenedId();
			ConfigViewLayout.Entry entry = ConfigViewLayout.Entry.of(
					ConfigOptionGrouping.groupKey(shortenedId, option.getOption().getCategory().name()),
					name, shortenedId, getUICommentForOption(option.getOption()).getString()
			);
			return new OptionRow(entry, row);
		}

		private <T> OptionRow createValueRow(PlayerConfigStringableOptionClientStorage<T> option, SimpleValueWidgetListElement<T, ?> element, PlayerConfigClientStorage source, boolean mutable){
			return createRow(option, element, source, mutable, target -> {
				element.setDraftValue(target);
				option.setCastValue(target);
				OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().syncToServer(source, option);
			});
		}

		private <T> OptionRow createIterationRow(PlayerConfigStringableOptionClientStorage<T> option, int elementWidth, int elementHeight, Component optionTitle, List<FormattedCharSequence> tooltip, PlayerConfigClientStorage source, boolean mutable){
			return createValueRow(option, createIterationWidgetListElement(option, elementWidth, elementHeight, optionTitle, tooltip, source), source, mutable);
		}

		private <T> OptionRow createTextRow(PlayerConfigStringableOptionClientStorage<T> option, TextWidgetListElement element, PlayerConfigClientStorage source, boolean mutable){
			return createRow(option, element, source, mutable, target -> element.setValueAndConfirm(target == null ? "" : option.getStringWriterCast().apply(target)));
		}

		private boolean isMutable(PlayerConfigClientStorage data, PlayerConfigStringableOptionClientStorage<?> option){
			// [Team Claims] team claims always grant the whole party full access, so the exception
			// group is locked on a team sub-config (the server rejects changing it as well)
			if(option.getOption() == PlayerConfigOptions.FULL_ACCESS
					&& xaero.pac.common.server.claims.TeamClaimsIntegration.isTeamSubId(data.getSubId()))
				return false;
			// [Team Claims] the server only lets the party owner/admins edit a team sub-config
			// (PlayerConfig.tryToSet), so a non-admin viewing their own team sub-config gets a
			// read-only screen here too instead of edits that the server will just reject
			if(isOwnTeamSubConfig(data) && !localPlayerIsPartyOwnerOrAdmin())
				return false;
			return data.getPermissions().canEdit() &&
					(option.isPlayerMutable() || manager.isAdmin() && option.isAdminMutable());
		}

		// [Team Claims] true only for the local player's own team_* sub-config: not another
		// player's config opened by an OP, and not a server/default/expired/wilderness/party-claims
		// config, all of which have a null sub ID or a non-PLAYER type or a non-null owner
		private boolean isOwnTeamSubConfig(PlayerConfigClientStorage data){
			return xaero.pac.common.server.claims.TeamClaimsIntegration.isTeamSubId(data.getSubId())
					&& data.getMain().getType() == PlayerConfigType.PLAYER
					&& data.getMain().getOwner() == null;
		}

		// [Team Claims] falls through (true) when the local player's party/member info isn't
		// available yet, so the stock mutability logic above decides instead of locking the widgets
		private boolean localPlayerIsPartyOwnerOrAdmin(){
			var localPlayer = Minecraft.getInstance().player;
			if(localPlayer == null)
				return true;
			var party = OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getClientPartyStorage().getParty();
			if(party == null)
				return true;
			var member = party.getMemberInfo(localPlayer.getUUID());
			if(member == null)
				return true;
			return member.isOwner() || member.getRank() == xaero.pac.common.parties.party.member.PartyMemberRank.ADMIN;
		}

		public PlayerConfigScreen build() {
			if(manager == null || data == null)
				throw new IllegalStateException();
			if((data.getType() == PlayerConfigType.PLAYER || data.getType() == PlayerConfigType.PARTY_CLAIMS)
					&& defaultPlayerConfigData == null)
				throw new IllegalStateException();
			if((data.getType() == PlayerConfigType.SERVER || data.getType() == PlayerConfigType.PARTY_CLAIMS)
					&& mainPlayerConfigData == null)
				throw new IllegalStateException();
			boolean anotherPlayer = data.getType() == PlayerConfigType.PLAYER && data.getOwner() != null;
			if(anotherPlayer && otherPlayerName == null)
				throw new IllegalStateException();
			List<WidgetListElement<?>> elements = listFactory.get();
			int elementWidth = 200;
			int elementHeight = 20;
			int optionWidth = elementWidth - ConfigOptionRowListElement.RESET_SLOT;//[Team Claims] the reset button is at the end of the row
			List<OptionRow> optionRows = new ArrayList<>();
			BiConsumer<PlayerConfigScreen, Button> refreshHandler;
			if(anotherPlayer)
				refreshHandler = (s,b) -> s.minecraft.setScreen(new OtherPlayerConfigWaitScreen(s.escape, s.parent, otherPlayerName));
			else
				refreshHandler = (s,b) -> s.minecraft.setScreen(build());

			Component mainTitle = this.title;
			Component title = null;
			if(mainTitle == null) {
				if(data.getType() == PlayerConfigType.PLAYER)
					mainTitle = Component.translatable("gui.xaero_pac_ui_my_player_config");
				else if(data.getType() == PlayerConfigType.SERVER)
					mainTitle = Component.translatable("gui.xaero_pac_ui_server_claims_config");
				else if(data.getType() == PlayerConfigType.PARTY_CLAIMS)
					mainTitle = Component.translatable("gui.xaero_pac_ui_party_claims_config");
				else
					mainTitle = Component.translatable("gui.xaero_pac_ui_player_config");
			}
			boolean syncInProgress = data.isSyncInProgress();
			boolean hasSubConfigs = data.getType().supportsSubConfigs();
			if(!syncInProgress && hasSubConfigs) {
				addSubConfigControls(elements, elementWidth, elementHeight);
				title = Component.translatable(
						"gui.xaero_pac_ui_player_config_sub",
						mainTitle,
						data.getSelectedSubConfig()
				);
			}
			if(title == null)
				title = mainTitle;

			boolean subConfigSelected = data.isSubConfigSelected();
			PlayerConfigClientStorage optionValueSourceData = subConfigSelected ? data.getOrCreateSubConfig(data.getSelectedSubConfig()) : data;
			boolean beingDeleted = optionValueSourceData.isBeingDeleted();
			Stream<PlayerConfigStringableOptionClientStorage<?>> optionStream = syncInProgress || beingDeleted ?
					Stream.empty() :
					optionValueSourceData.typedOptionStream();
			optionStream.forEach(optionStorage -> {
				if(!optionStorage.isSyncable()
						|| !optionStorage.isDirectlyConfigurable()
						|| !optionStorage.getOption().getConfigTypeFilter().test(optionValueSourceData.getType())
						|| optionStorage.getOption() == PlayerConfigOptions.USED_SUBCLAIM
						|| optionStorage.getOption() == PlayerConfigOptions.USED_SERVER_SUBCLAIM
						|| optionStorage.getOption() == PlayerConfigOptions.USED_PARTY_SUBCLAIM
				)
					return;
				if(optionValueSourceData instanceof PlayerSubConfigClientStorage &&
						(!optionValueSourceData.getType().hasDimensionSubConfigs() || !optionStorage.getOption().isOnlyDimensionOverridable())
						&& !optionStorage.getOption().isOverridable())
					return;
				final boolean mutable = isMutable(optionValueSourceData, optionStorage);
				Class<?> type = optionStorage.getType();
				Component optionTitle = Component.translatable(optionStorage.getTranslation(), optionStorage.getTranslationArgs());
				List<FormattedCharSequence> tooltip = Minecraft.getInstance().font.split(getUICommentForOption(optionStorage.getOption()), 200);
				if(type == Boolean.class) {
					@SuppressWarnings("unchecked")
					PlayerConfigStringableOptionClientStorage<Boolean> booleanOption = (PlayerConfigStringableOptionClientStorage<Boolean>) optionStorage;
					optionRows.add(createValueRow(booleanOption, createOnOffWidgetListElement(booleanOption, optionWidth,elementHeight, optionTitle, tooltip, optionValueSourceData), optionValueSourceData, mutable));
				} else if(optionStorage.getOption() instanceof PlayerConfigListIterationOptionSpec) {
					optionRows.add(createIterationRow(optionStorage, optionWidth, elementHeight, optionTitle, tooltip, optionValueSourceData, mutable));
				} else {
					Object value = getOptionValue(optionStorage);

					Predicate<String> filter = Objects::nonNull;
					if(type == Integer.class) {
						if(optionStorage.getOption() instanceof PlayerConfigHexOptionSpec)
							filter = s -> s != null && s.matches("^[0-9A-Fa-f]*$");
						else
							filter = s -> s != null && s.matches("^[-0-9]*$");
						
					} else if(type == Double.class || type == Float.class)
						filter = s -> s != null && s.matches("^[-\\.0-9]*$");
					
					TextWidgetListElement.Builder elementBuilder = TextWidgetListElement.Builder.begin()
							.setW(optionWidth)
							.setH(elementHeight)
							.setTitle(optionTitle)
							.setTooltip(tooltip)
							.setMutable(mutable)
							.setStartValue(value == null ? "" : optionStorage.getStringWriterCast().apply(value))
							.setFilter(filter)
							.setValidator(s -> subConfigSelected && s.isEmpty() || optionStorage.getStringValidator().test(optionValueSourceData, s))
							.setResponder((el, s) -> {
								if(!mutable)
									return;
								//on value change
								Object newValue;
								if(subConfigSelected && s.isEmpty())
									newValue = null;
								else
									newValue = optionStorage.getCommandInputParser().apply(s);
								optionStorage.setCastValue(newValue);
								OpenPartiesAndClaims.INSTANCE.getClientDataInternal().getPlayerConfigClientSynchronizer().syncToServer(optionValueSourceData, optionStorage);
							});
					if(optionStorage.getOption() instanceof PlayerConfigStringOptionSpec)
						elementBuilder.setMaxLength(((PlayerConfigStringOptionSpec) optionStorage.getOption()).getMaxLength());
					else if(optionStorage.getOption() instanceof PlayerConfigHexOptionSpec)
						elementBuilder.setMaxLength(8);
					optionRows.add(createTextRow(optionStorage, elementBuilder.build(), optionValueSourceData, mutable));
				}
			});

			return new PlayerConfigScreen(elements, optionRows, ConfigViewState.forType(data.getType().name()), listFactory.get(), refreshHandler, escape, parent, title, data, optionValueSourceData, syncInProgress, optionValueSourceData.isBeingDeleted(), mainTitle, otherPlayerName);
		}

		public static Builder begin(ListFactory listFactory) {
			return new Builder(listFactory).setDefault();
		}
		
	}

}
