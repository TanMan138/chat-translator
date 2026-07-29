package com.tanman.chattranslator.client;

import com.tanman.chattranslator.client.command.TranslateCommand;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.event.IncomingChatHandler;
import com.tanman.chattranslator.client.event.OutgoingChatHandler;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.LanguageDetector;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import com.tanman.chattranslator.client.translation.backend.TranslationService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ChatTranslatorClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Path modelsDir = FabricLoader.getInstance().getGameDir().resolve("chattranslator").resolve("models");

		TranslatorConfig config = TranslatorConfig.load();
		TranslationState state = new TranslationState();
		config.applyTo(state);
		state.setOnUserChanged(() -> {
			config.captureFrom(state);
			config.save();
		});

		LanguageDetector detector = new LanguageDetector();
		ModelManager modelManager = new ModelManager(modelsDir);
		ModelDownloader downloader = new ModelDownloader();
		Translator translator = new Translator();
		TranslationService translationService = new TranslationService(
				config, modelManager, downloader, translator);

		ChatTranslatorServices.init(state, modelManager, translator, config, translationService);

		IncomingChatHandler.register(state, detector, translationService);
		OutgoingChatHandler.register(state, modelManager, downloader, translator, translationService, config);
		TranslateCommand.register(state, modelManager, translator);
	}
}
