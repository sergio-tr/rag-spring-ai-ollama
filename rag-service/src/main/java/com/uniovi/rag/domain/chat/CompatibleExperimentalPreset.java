package com.uniovi.rag.domain.chat;

public record CompatibleExperimentalPreset(
        ChatExperimentalPresetCatalogItem preset, PresetIndexCompatibility compatibility) {}
