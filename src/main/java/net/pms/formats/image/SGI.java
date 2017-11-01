package net.pms.formats.image;

import net.pms.dlna.protocolinfo.MimeType;

/**
 * A representation of the Silicon Graphics SGI image format, also known as RLE
 * or Haeberli.
 *
 * @author Nadahar
 */
public class SGI extends ImageBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.SGI;
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] {
			"sgi",
			"rle"
		};
	}

	@Override
	public MimeType mimeType() {
		/*
		 * image/sgi,
		 * image/x-sgi,
		 * image/x-sgi-rgba
		 */
		return MimeType.FACTORY.createMimeType("image","sgi");
	}
}
