package com.tana.spa.feature.assistant.di

import com.tana.spa.feature.assistant.data.ScriptedAssistantGateway
import com.tana.spa.feature.assistant.domain.gateway.AssistantGateway
import com.tana.spa.feature.assistant.domain.usecase.SendChatMessageUseCase
import com.tana.spa.feature.assistant.domain.usecase.StartChatUseCase
import com.tana.spa.feature.assistant.presentation.manipulator.ChatViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val assistantModule: Module = module {
    // TODO: bind the real AI here. ScriptedAssistantGateway is a stand-in that asks fixed questions.
    single<AssistantGateway> { ScriptedAssistantGateway() }
    factory { StartChatUseCase(get()) }
    factory { SendChatMessageUseCase(get()) }
    viewModelOf(::ChatViewModel)
}
