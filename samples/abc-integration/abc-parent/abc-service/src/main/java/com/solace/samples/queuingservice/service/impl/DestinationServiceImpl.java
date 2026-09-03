package com.solace.samples.queuingservice.service.impl;

import com.solace.samples.queuingservice.exception.DestinationAlreadyExistsException;
import com.solace.samples.queuingservice.model.Destination;
import com.solace.samples.queuingservice.model.DestinationRequest;
import com.solace.samples.queuingservice.service.DestinationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of DestinationService.
 */
@Service
public class DestinationServiceImpl implements DestinationService {

    private static final Logger logger = LoggerFactory.getLogger(DestinationServiceImpl.class);

    // Map to store destination name -> message count
    private final Map<String, AtomicInteger> destinationMap = new ConcurrentHashMap<>();

    @Override
    public List<Destination> listDestinations() {
        logger.info("Listing all destinations, found {} destinations", destinationMap.size());
        return destinationMap.entrySet().stream()
                .map(entry -> new Destination(entry.getKey(), entry.getValue().get()))
                .toList();
    }

    @Override
    public Destination createDestination(DestinationRequest destinationRequest) {
        String name = destinationRequest.getName();
        logger.info("Creating new destination: {}", name);
        
        if (destinationExists(name)) {
            logger.warn("Destination already exists: {}", name);
            throw new DestinationAlreadyExistsException("Destination already exists: " + name);
        }
        
        destinationMap.putIfAbsent(name, new AtomicInteger(0));
        return new Destination(name, 0);
    }

    @Override
    public boolean destinationExists(String name) {
        return destinationMap.containsKey(name);
    }

    @Override
    public int getMessageCount(String name) {
        AtomicInteger count = destinationMap.get(name);
        int messageCount = count != null ? count.get() : 0;
        logger.debug("Message count for destination {}: {}", name, messageCount);
        return messageCount;
    }

    @Override
    public void updateMessageCount(String name, int delta) {
        destinationMap.computeIfAbsent(name, k -> new AtomicInteger(0)).addAndGet(delta);
    }
    
    @Override
    public boolean deleteDestination(String name) {
        logger.info("Deleting destination: {}", name);
        return destinationMap.remove(name) != null;
    }
}
