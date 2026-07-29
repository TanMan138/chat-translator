package com.tanman.chattranslator.client;

import com.tanman.chattranslator.client.command.TranslateCommand;
import com.tanman.chattranslator.client.event.IncomingChatHandler;
import com.tanman.chattranslator.client.event.OutgoingChatHandler;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.LanguageDetector;
import com.tanman.chattranslator.client.translation.ModelDownloader;
import com.tanman.chattranslator.client.translation.ModelManager;
import com.tanman.chattranslator.client.translation.Translator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ChatTranslatorClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Path modelsDir = FabricLoader.getInstance().getGameDir().resolve("chattranslator").resolve("models");

		TranslationState state = new TranslationState();
		LanguageDetector detector = new LanguageDetector();
		ModelManager modelManager = new ModelManager(modelsDir);
		ModelDownloader downloader = new ModelDownloader();
		Translator translator = new Translator();

		IncomingChatHandler.register(state, detector, modelManager, downloader, translator);
		OutgoingChatHandler.register(state, modelManager, downloader, translator);
		TranslateCommand.register(state);
	}
}
