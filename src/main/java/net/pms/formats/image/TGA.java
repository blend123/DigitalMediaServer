package net.pms.formats.image;

import net.pms.dlna.protocolinfo.MimeType;

/**
 * A representation of the Truevision Targa Graphic file format.
 *
 * @author Nadahar
 */
public class TGA extends ImageBase {

	@Override
	public Identifier getIdentifier() {
		return Identifier.TGA;
	}

	@Override
	public String[] getSupportedExtensions() {
		return new String[] {
			"tga",
			"icb",
			"vda",
			"vstrle"
		};
	}

	@Override
	public MimeType mimeType() {
		/*
		 * application/tga,
		 * application/x-tga,
		 * application/x-targa,
		 * image/tga,
		 * image/x-tga,
		 * image/targa,
		 * image/x-targa
		 */
		return MimeType.FACTORY.createMimeType("image","x-tga");
	}

}
