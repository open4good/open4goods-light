package org.open4goods.commons;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.open4goods.config.TestConfig;
import org.open4goods.exceptions.InvalidParameterException;
import org.open4goods.exceptions.TechnicalException;
import org.open4goods.services.GoogleTaxonomyService;
import org.open4goods.services.IcecatService;
import org.open4goods.services.RemoteFileCachingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestConfig.class)
public class IcecatFeaturesServiceTest {

	@Autowired private IcecatService featureService;

    
    @Test
    public void testLoadFile() throws IOException, InvalidParameterException, TechnicalException {
        
       
    	
    	
//    	featureService.loadFeatures();
   
        
       
    }
}