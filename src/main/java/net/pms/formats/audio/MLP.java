package net.pms.formats.audio;

import net.pms.dlna.protocolinfo.MimeType;
import net.pms.dlna.protocolinfo.KnownMimeTypes;
import net.pms.formats.AudioAsVideo;

public class MLP extends AudioBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.MLP;
	}

	public MLP() {
		secondaryFormat = new AudioAsVideo();
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] { "mlp" };
	}

	@Override
	public MimeType mimeType() {
		return KnownMimeTypes.MLP;
	}
}
