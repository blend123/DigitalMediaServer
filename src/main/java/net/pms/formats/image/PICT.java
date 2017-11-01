package net.pms.formats.image;

import net.pms.dlna.protocolinfo.MimeType;


/**
 * A representation of the Apple PICT format.
 *
 * @author Nadahar
 */
public class PICT extends ImageBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.PICT;
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] {
			"pict",
			"pct",
			"pic"
		};
	}

	@Override
	public MimeType mimeType() {
		return MimeType.FACTORY.createMimeType("image","x-pict");
	}
}
