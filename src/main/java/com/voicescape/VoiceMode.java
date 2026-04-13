package com.voicescape;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VoiceMode
{
	PUSH_TO_TALK("Push-to-Talk"),
	VOICE_ACTIVITY("Voice Activity");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
