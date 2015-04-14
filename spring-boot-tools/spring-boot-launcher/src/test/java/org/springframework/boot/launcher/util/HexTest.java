package org.springframework.boot.launcher.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class HexTest {

	@Test
	public void roundTrip() {
		byte[] data = new byte[0xff];
		for (int i = 0; i < data.length; i++) { data[i] = (byte) i; }
		char[] encoded = Hex.encode(data);
		byte[] decoded = Hex.decode(encoded);
		Assert.assertArrayEquals(data, decoded);
	}

}
