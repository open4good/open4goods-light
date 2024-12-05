package org.open4goods.api.services.aggregation.services.realtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.open4goods.api.services.aggregation.AbstractAggregationService;
import org.open4goods.commons.config.yml.attributes.AttributeConfig;
import org.open4goods.commons.config.yml.attributes.AttributeParser;
import org.open4goods.commons.config.yml.ui.AttributesConfig;
import org.open4goods.commons.config.yml.ui.VerticalConfig;
import org.open4goods.commons.exceptions.AggregationSkipException;
import org.open4goods.commons.exceptions.ResourceNotFoundException;
import org.open4goods.commons.exceptions.ValidationException;
import org.open4goods.commons.model.attribute.Attribute;
import org.open4goods.commons.model.constants.ReferentielKey;
import org.open4goods.commons.model.data.DataFragment;
import org.open4goods.commons.model.product.IndexedAttribute;
import org.open4goods.commons.model.product.Product;
import org.open4goods.commons.model.product.ProductAttribute;
import org.open4goods.commons.model.product.ProductAttributes;
import org.open4goods.commons.model.product.SourcedAttribute;
import org.open4goods.commons.services.BrandService;
import org.open4goods.commons.services.IcecatService;
import org.open4goods.commons.services.VerticalsConfigService;
import org.slf4j.Logger;

public class AttributeRealtimeAggregationService extends AbstractAggregationService {

	private final BrandService brandService;

	private VerticalsConfigService verticalConfigService;
	private IcecatService featureService;

	public AttributeRealtimeAggregationService(final VerticalsConfigService verticalConfigService, BrandService brandService, final Logger logger, IcecatService featureService) {
		super(logger);
		this.verticalConfigService = verticalConfigService;
		this.brandService = brandService;
		this.featureService = featureService;
	}

	@Override
	public void onProduct(Product data, VerticalConfig vConf) throws AggregationSkipException {

		//////////////////////////////////////////
		// Cleaning attributes that must be discarded
		//////////////////////////////////////////

		// Remove excluded attributes
		// TODO(p3,perf) / Usefull for batch mode, could remove once initial sanitization
		vConf.getAttributesConfig().getExclusions().forEach(e -> {
			data.getAttributes().getAll().remove(e);
		});

		

		/////////////////////////////////////////////////
		// Cleaning brands
		// NOTE : Should be disabled after recovery batch, but need to be run each time to
		// take in account modifications of configurable brandAlias() and brandExclusions()
		/////////////////////////////////////////////////
		
		String actualBrand = data.brand();
		Map<String,String> akaBrands = new HashMap<>(data.getAkaBrands());
		
		data.getAttributes().getReferentielAttributes().remove(ReferentielKey.BRAND);
		data.akaBrands().clear();
		data.addBrand(null, actualBrand, vConf.getBrandsExclusion(), vConf.getBrandsAlias());
		
		akaBrands.entrySet().forEach(e -> {
			data.addBrand(e.getKey(), e.getValue(), vConf.getBrandsExclusion(), vConf.getBrandsAlias());
		});
		
		
		
		
		// Attributing taxomy to attributes
		data.getAttributes().getAll().values().forEach(a -> {
			Set<Integer> icecatTaxonomyIds = featureService.resolveFeatureName(a.getName());
			if (null != icecatTaxonomyIds) {
				dedicatedLogger.info("Found icecat taxonomy for {} : {}", a.getName(), icecatTaxonomyIds);
				a.setIcecatTaxonomyIds(icecatTaxonomyIds);
			}
		});

		///////////////////////////////////////////////////
		// Extracting indexed attributes
		//////////////////////////////////////////////////
		AttributesConfig attributesConfig = vConf.getAttributesConfig();
		
		
		Map<String,IndexedAttribute> indexed = new HashMap<String, IndexedAttribute>();
		
		
		for (ProductAttribute attr : data.getAttributes().getAll().values()) {

			// Checking if a potential AggregatedAttribute
			AttributeConfig attrConfig = attributesConfig.resolveFromProductAttribute(attr);
			
			// We have a "raw" attribute that matches an aggregationconfig
			if (null != attrConfig) {

				try {

					// Applying parsing rule
					String cleanedValue =  parseAttributeValue(attr.getValue(), attrConfig);

					if (StringUtils.isEmpty(cleanedValue)) {
						dedicatedLogger.error("Empty indexed attribute value {}:{}",attrConfig.getKey(),attr.getValue());
						continue;
					}
					
					IndexedAttribute indexedAttr = indexed.get(attrConfig.getKey());
					if (null != indexedAttr) {
						dedicatedLogger.info("Duplicate attribute candidate for indexation, for GTIN : {} and attrs {}",data.getId(), attrConfig.getKey());
						if (!cleanedValue.equals(indexedAttr.getValue() )) {
							// TODO(p3,design) : Means we have multiple attributes matching for indexed . Have a merge strategy
							dedicatedLogger.error("Value mismatch for attribute {} : {}<>{}",attr.getName(),cleanedValue, indexedAttr.getValue());
						} 						
					} else {
						 indexedAttr = new IndexedAttribute(attrConfig.getKey(), cleanedValue);
					}
					
					indexedAttr.getSource().addAll(attr.getSource());					
					indexed.put(attrConfig.getKey(), indexedAttr);
					
				} catch (Exception e) {
					dedicatedLogger.error("Attribute parsing fail for matched attribute {}", attrConfig.getKey(),e);
				}
			}
		}
		
		
		// Replacing all previously indexed
		data.getAttributes().setIndexed(indexed);
		
		
		
		///////////////////////////////////////////
		// Setting excluded state
		////////////////////////////////////////// 
		
		data.setExcluded(shouldExclude(data,vConf));
		
	}

	/**
	 * Set the product in excluded state (will not be exposed through indexation, searchservice,..) 
	 * @param data
	 */
	private boolean shouldExclude(Product data, VerticalConfig vConf) {
		// On brand
		if (StringUtils.isEmpty(data.brand())) {
			dedicatedLogger.info("Excluded because brand is missing : {}", data );
			return true;
		}
		
		// On model
		if (StringUtils.isEmpty(data.model())) {
			dedicatedLogger.info("Excluded because model is missing : {}", data );
			return true;
		}
		
		Set<String> attrKeys = data.getAttributes().getattributesAsStringKeys();
		if (vConf.getRequiredAttributes() != null && !attrKeys.containsAll(vConf.getRequiredAttributes())) {
			dedicatedLogger.info("Excluded because attributes are missing : {}", data );
			return true;
		}
		
		return false;
	}


	
	/**
	 * On data fragment agg leveln we increment the "all" field, with sourced values
	 * for new or existing attributes. product
	 *
	 * @param dataFragment
	 * @param p
	 * @param match2
	 */
	@Override
	public Map<String, Object> onDataFragment(final DataFragment dataFragment, final Product product, VerticalConfig vConf) throws AggregationSkipException {

		try {

//			AttributesConfig attributesConfig = vConf.getAttributesConfig();

//			// Remove excluded attributes
//			if (dataFragment.getAttributes().removeIf(e -> attributesConfig.getExclusions().contains(e.getName()))) {
//				dedicatedLogger.info("Attributes have been removed for {}", product.gtin());
//			}

			/////////////////////////////////////////
			// Incrementing "all" attributes
			/////////////////////////////////////////
			for (Attribute attr : dataFragment.getAttributes()) {

				ProductAttribute agg = product.getAttributes().getAll().get(attr.getName());

				if (null == agg) {
					// A first time match
					agg = new ProductAttribute();
					agg.setName(attr.getName());
				}

				agg.addSourceAttribute(new SourcedAttribute(attr, dataFragment.getDatasourceName()));

				// Replacing new AggAttribute in product
				product.getAttributes().getAll().put(agg.getName(), agg);

			}


			// Checking model name from product words
//			completeModelNames(product, dataFragment.getReferentielAttributes().get(ReferentielKey.MODEL));

			/////////////////////////////////////////
			// Update referentiel attributes
			/////////////////////////////////////////
			handleReferentielAttributes(dataFragment, product, vConf);
			// TODO : Add BRAND / MODEL from matches from attributes

	
		} catch (Exception e) {
			dedicatedLogger.error("Unexpected error", e);
		}

		onProduct(product, vConf);
		return null;
	}

//	/**
//	 * Complete the model names by looking in product words for sequence starting with the shortest model name.
//	 * @param product
//	 * @param string
//	 */
//	private void completeModelNames(Product product, String string) {
//		// Get the known model names
//		Set<String> models = new HashSet<>();
//		if (!StringUtils.isEmpty(string)) {
//			models.add(string);
//		}
//		product.getAlternativeBrands().forEach(e -> models.add(e.getValue()));
//		
//		
//		// Compute the bag of known words
//		Set<String> words = new HashSet<>();
//		product.getDescriptions().forEach(e -> {
//			words.addAll(Arrays.asList(e.getContent().getText().split(" ")));
//		});
//		
//		product.getNames().getOfferNames().forEach(e -> {
//			words.addAll(Arrays.asList(e.split(" ")));
//		});
//		
//		
//		String shortest = product.shortestModel();
//		// Iterating on words to find the one who have matching starts with known model names
//		for (String w : words) {
//			w = w.toUpperCase();
//			if ((w.startsWith(shortest) || shortest.startsWith(w))  && !w.equals(shortest)) {
//				
//				if (StringUtils.isAlpha(w.substring(w.length()-1))) {
//					dedicatedLogger.info("Found a alternate model for {} in texts : {}", shortest, w);
//					product.addModel(w);
//					
//				}
//				
//			}
//		}
//	}
//

	/**
	 *
	 * @param matchedFeatures
	 * @param unmatchedFeatures
	 * @return
	 */
	private void  extractFeatures(ProductAttributes attributes) {

		attributes.getFeatures().clear();
		
		
		Map<String, ProductAttribute> features = attributes.getAll().entrySet().stream()
				.filter(e -> e.getValue().isFeature())
			    .collect(Collectors.toMap(
			            Map.Entry::getKey,    // key mapper: uses the key from each entry
			            Map.Entry::getValue   // value mapper: uses the value from each entry
			        ));
		
		attributes.getFeatures().addAll(features.keySet());
		
	}

	/**
	 * Returns if an attribute is a feature, by comparing "yes" values from config
	 * 
	 * @param e
	 * @return
	 */
	private boolean isFeatureAttribute(Attribute e, AttributesConfig attributesConfig) {
		return e.getRawValue() == null ? false : attributesConfig.getFeaturedValues().contains(e.getRawValue().trim().toUpperCase());
	}

	/**
	 * Handles referential attributes of a data fragment and updates the product
	 * output accordingly. This method updates or adds referential attributes, while
	 * also handling conflicts and logging them.
	 *
	 * @param fragment The data fragment containing referential attributes.
	 * @param output   The product output to which referential attributes are to be
	 *                 added or updated.
	 */
	private void handleReferentielAttributes(DataFragment fragment, Product output, VerticalConfig vConf) {
		
		///////////////////////
		// Adding brand
		///////////////////////
		String brand = fragment.brand();
		if (!StringUtils.isEmpty(brand)) {
			output.addBrand(fragment.getDatasourceName(), brand, vConf.getBrandsExclusion(), vConf.getBrandsAlias());
		}
		
		
		///////////////////////
		// Adding model
		///////////////////////
		String model = fragment.getReferentielAttributes().get(ReferentielKey.MODEL);
		if (!StringUtils.isEmpty(model)) {
			output.addModel(model);
		}

		///////////////////////
		// Handling gtin (NOTE : useless since gtin is used as ID, so coupling is done previously
		///////////////////////
		String gtin = fragment.gtin();
		if (!StringUtils.isEmpty(gtin)) {
			String existing = output.gtin();
			
			if (!existing.equals(gtin)) {
				try {
					long existingGtin = Long.parseLong(existing);
					long newGtin = Long.parseLong(gtin);
					if (existingGtin != newGtin) {
						dedicatedLogger.error("Overriding GTIN from {} to {}", existing, newGtin);
						output.getAttributes().getReferentielAttributes().put(ReferentielKey.GTIN, gtin);
					} 
				} catch (NumberFormatException e) {
					dedicatedLogger.error("Invalid GTIN format: existing = {}, new = {}", existing, gtin, e);
				}
			}
			
		}
		
	
	}

	/**
	 * Type attribute and apply parsing rules. Return null if the Attribute fail to
	 * exact parsing rules
	 *
	 * @param translated
	 * @param attributeConfigByKey
	 * @return
	 * @throws ValidationException
	 */
	public String parseAttributeValue(final String source, final AttributeConfig conf) throws ValidationException {

		String string = source;
		///////////////////
		// To upperCase / lowerCase
		///////////////////
		if (conf.getParser().getLowerCase()) {
			
			string = string.toLowerCase();
		}

		if (conf.getParser().getUpperCase()) {
			string = string.toUpperCase();
		}

		//////////////////////////////
		// Deleting arbitrary tokens
		//////////////////////////////

		if (null != conf.getParser().getDeleteTokens()) {
			for (final String token : conf.getParser().getDeleteTokens()) {
				string = string.replace(token, "");
			}
		}

		///////////////////
		// removing parenthesis tokens
		///////////////////
		if (conf.getParser().isRemoveParenthesis()) {
			string = string.replace("\\(.*\\)", "");
		}

		///////////////////
		// Normalisation
		///////////////////
		if (conf.getParser().getNormalize()) {
			string = StringUtils.normalizeSpace(string);
		}

		///////////////////
		// Trimming
		///////////////////
		if (conf.getParser().getTrim()) {
			string = string.trim();
		}

		///////////////////
		// Exact match option
		///////////////////

		if (null != conf.getParser().getTokenMatch()) {
			boolean found = false;

			final String val = string;
			for (final String match : conf.getParser().getTokenMatch()) {
				if (val.contains(match)) {
					string = match;
					found = true;
					break;
				}
			}

			if (!found) {
				throw new ValidationException("Token " + string + " does not match  any fixed attribute ");
			}

		}

		/////////////////////////////////
		// FIXED TEXT MAPPING
		/////////////////////////////////
		if (!conf.getMappings().isEmpty()) {
			
			string = conf.getMappings().get(string);
		}

		/////////////////////////////////
		// Checking preliminary result
//		/////////////////////////////////
//
//		if (null == string) {
//			throw new ValidationException("Null rawValue in attribute " + string);
//		}

		////////////////////////////////////
		// Applying specific parser instance
		/////////////////////////////////////

		if (!StringUtils.isEmpty(conf.getParser().getClazz())) {
			try {
				final AttributeParser parser = conf.getParserInstance();
				string  = parser.parse(string, conf);
			} catch (final ResourceNotFoundException e) {
				dedicatedLogger.warn("Error while applying specific parser for {}", conf.getParser().getClazz(), e);
				throw new ValidationException(e.getMessage());
			} catch (final Exception e) {
				dedicatedLogger.error("Unexpected exception while parsing {} with {}", string, conf.getParser().getClazz(), e);
				throw new ValidationException(e.getMessage());
			}
		}

		return string;

	}

}
