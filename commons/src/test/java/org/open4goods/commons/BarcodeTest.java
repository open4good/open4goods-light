package org.open4goods.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.open4goods.helper.IdHelper;
import org.open4goods.model.BarcodeType;
import org.open4goods.services.BarcodeValidationService;

public class BarcodeTest {

	@Test
	public void test() {
		BarcodeValidationService service = new BarcodeValidationService();
		
		
		// Testing ean13
		assertEquals(BarcodeType.GTIN_13, service.sanitize("8436542858045").getKey());
		assertEquals(BarcodeType.GTIN_13, service.sanitize("3462117246967").getKey());
		

		// Testing ean8
		assertEquals(BarcodeType.GTIN_8, service.sanitize("40170725").getKey());

		assertEquals(BarcodeType.GTIN_12, service.sanitize("123601057072").getKey());

		
		// Testing ISBN 13
		assertEquals(BarcodeType.ISBN_13, service.sanitize("9782298135039").getKey());
		assertEquals(BarcodeType.ISBN_13, service.sanitize("978-614-404-018-8").getKey());
		

		// Testing ISBN 10 (converted to isbn13)
		assertEquals(BarcodeType.ISBN_13, service.sanitize("0198526636").getKey());
		
		
		
		// Testing invalid
		assertEquals(BarcodeType.UNKNOWN, service.sanitize("8436542859045").getKey());
			
		
		
		
		
	}
//	
	
	


}
