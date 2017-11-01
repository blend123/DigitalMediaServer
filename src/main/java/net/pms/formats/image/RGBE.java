package net.pms.formats.image;

import net.pms.dlna.protocolinfo.MimeType;

/**
 * A representation of the former Radiance HDR now known as RGBE format.
 *
 * @author Nadahar
 */
public class RGBE extends ImageBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.RGBE;
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] {
			"hdr",
			"rad",
			"rgbe",
			"xyze"
		};
	}

	@Override
	public MimeType mimeType() {
		return MimeType.FACTORY.createMimeType("image","vnd.radiance");
	}
}
