package com.tanman.chattranslator.client.guide;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game beginner guide with page navigation.
 */
public final class GuideScreen extends Screen {

    private static final Component TITLE = Component.literal("Chat Translator Guide");

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private int pageIndex;
    private StringWidget titleWidget;
    private MultiLineTextWidget bodyWidget;
    private StringWidget pageIndicator;

    public GuideScreen(Screen parent) {
        this(parent, 0);
    }

    public GuideScreen(Screen parent, int startPage) {
        super(TITLE);
        this.parent = parent;
        this.pageIndex = Math.clamp(startPage, 0, PlayerGuide.pageCount() - 1);
    }

    public static void open(Screen parent) {
        open(parent, 0);
    }

    public static void open(Screen parent, int startPage) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new GuideScreen(parent, startPage)));
        }
    }

    @Override
    protected void init() {
        layout.addTitleHeader(TITLE, font);

        LinearLayout body = LinearLayout.vertical().spacing(8);
        body.defaultCellSetting().alignHorizontallyCenter();

        PlayerGuide.Page page = PlayerGuide.page(pageIndex);
        titleWidget = new StringWidget(Component.literal(page.title()), font);
        titleWidget.setMaxWidth(320);
        body.addChild(titleWidget);

        bodyWidget = new MultiLineTextWidget(Component.literal(page.body()), font);
        bodyWidget.setMaxWidth(320);
        bodyWidget.setCentered(false);
        body.addChild(bodyWidget);

        pageIndicator = new StringWidget(Component.empty(), font);
        pageIndicator.setMaxWidth(320);
        body.addChild(pageIndicator);

        layout.addToContents(body, settings -> settings.alignHorizontallyCenter());

        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        footer.defaultCellSetting().alignHorizontallyCenter();

        footer.addChild(Button.builder(Component.literal("Back"), b -> changePage(-1))
                .width(90)
                .build());
        footer.addChild(Button.builder(Component.literal("Next"), b -> changePage(1))
                .width(90)
                .build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .width(90)
                .build());

        layout.addToFooter(footer, settings -> settings.alignHorizontallyCenter());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        refreshPageIndicator();
    }

    private void changePage(int delta) {
        int next = pageIndex + delta;
        if (next < 0 || next >= PlayerGuide.pageCount()) {
            return;
        }
        pageIndex = next;
        PlayerGuide.Page page = PlayerGuide.page(pageIndex);
        titleWidget.setMessage(Component.literal(page.title()));
        bodyWidget.setMessage(Component.literal(page.body()));
        refreshPageIndicator();
    }

    private void refreshPageIndicator() {
        if (pageIndicator != null) {
            pageIndicator.setMessage(Component.literal(
                    "Page " + (pageIndex + 1) + " of " + PlayerGuide.pageCount()));
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
