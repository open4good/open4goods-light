package org.open4goods.commons.store.repository;

import java.util.HashMap;
import java.util.Map;

import org.open4goods.commons.dao.ProductRepository;
import org.open4goods.commons.model.product.Product;
import org.open4goods.commons.model.product.ProductPartialUpdateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Worker thread that asynchronously dequeue the DataFragments from the file queue. It
 * @author goulven
 *
 */
public class PartialProductIndexationWorker implements Runnable {


	private static final Logger logger = LoggerFactory.getLogger(PartialProductIndexationWorker.class);

	/** The service used to "atomically" fetch and store / update DataFragments **/
	private final ProductRepository service;

	/** Size of pages that will be bulked to the DataFragmentStore**/
	private final int dequeuePageSize;

	/** The duration of the worker thread pause when nothing to get from the queue **/
	private final int pauseDuration;

	/**
	 * The worker name
	 */
	private final String workerName;

	/**
	 * Constructor
	 * @param owningService
	 * @param dequeuePageSize
	 */
	public PartialProductIndexationWorker(final ProductRepository owningService, final int dequeuePageSize, final int pauseDuration, String workerName) {
		service = owningService;
		this.dequeuePageSize = dequeuePageSize;
		this.pauseDuration = pauseDuration;
		this.workerName = workerName;
	}

	@Override
	public void run() {

		// TODO : exit thread condition
		while (true) {
			try {
				
				// Computing if items presents, and how many to take
				int itemsToTake = service.getPartialProductQueue().size();
				if (itemsToTake > dequeuePageSize) {
					itemsToTake = dequeuePageSize;
				}
				
				if (itemsToTake > 0) {
					// There is data to consume and queue consummation is enabled
					// A map to deduplicate --> MEANS WE CAN SOMETIMES LOOSE DATAFRAMENTS IF 2 ENTRIES ARE IN THE SAME BAG (no because we put back in queue)
					final Map<Long,ProductPartialUpdateHolder> buffer = new HashMap<>();	
										
					// Dequeuing
					for (int i = 0; i < itemsToTake; i++) {
						ProductPartialUpdateHolder item = service.getPartialProductQueue().take();
						
						if (buffer.containsKey(item.getProductId())) {
							logger.info("Putting back in queue : {}", item.getProductId() );
							service.getPartialProductQueue().put(item);
						} else {
							buffer.put(item.getProductId(),item);							
						}
					}
					
					service.bulkUpdateDocument(buffer.values());
					
					logger.warn ("{} has indexed {} products. {} Remaining in queue",workerName,  buffer.size(), service.getFullProductQueue().size());

				} else {
					try {
						logger.debug("No DataFragments to dequeue. Will sleep {}ms",pauseDuration);
						Thread.sleep(pauseDuration);
					} catch (final InterruptedException e) {
					}
				}
			} catch (final Exception e) {
				logger.error("Error while dequeing DataFragments",e);
			}
		}
	}
}