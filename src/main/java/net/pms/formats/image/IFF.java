package net.pms.formats.image;

import net.pms.dlna.protocolinfo.MimeType;

/**
 * A representation of the Interchange File Format.
 *
 * @author Nadahar
 */
public class IFF extends ImageBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.IFF;
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] {
			"iff"
		};
	}

	@Override
	public MimeType mimeType() {
		/*
		 * application/iff
		 * application/x-iff
		 * image/iff
		 * image/x-iff
		 */
		return MimeType.FACTORY.createMimeType("image","iff");
	}
}
