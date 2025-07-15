// Directory structure:
// com.company.agentrating
// ├── models
// │   ├── AgentRating.java
// │   └── ErrorCode.java
// ├── service
// │   └── AgentRatingService.java
// ├── store
// │   └── AgentStore.java
// ├── exceptions
// │   └── ServiceException.java
// ├── test
// │   └── AgentRatingServiceTest.java
// └── Main.java

// models/AgentRating.java
package models;

public class AgentRating implements Comparable<AgentRating> {
    private final String agentId;
    private final double avgRating;
    private final int totalRatings;

    public AgentRating(String agentId, double avgRating, int totalRatings) {
        this.agentId = agentId;
        this.avgRating = avgRating;
        this.totalRatings = totalRatings;
    }

    public String getAgentId() {
        return agentId;
    }

    public double getAvgRating() {
        return avgRating;
    }

    public int getTotalRatings() {
        return totalRatings;
    }

    @Override
    public int compareTo(AgentRating other) {
        int cmp = Double.compare(other.avgRating, this.avgRating);
        return (cmp != 0) ? cmp : this.agentId.compareTo(other.agentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRating that = (AgentRating) o;
        return agentId.equals(that.agentId);
    }

    @Override
    public int hashCode() {
        return agentId.hashCode();
    }

    @Override
    public String toString() {
        return agentId + " (" + avgRating + ", " + totalRatings + ")";
    }
}

// models/ErrorCode.java
package models;

public enum ErrorCode {
    INVALID_RATING,
    INVALID_AGENT_ID,
    INVALID_TOP_K_REQUEST
}

// exceptions/ServiceException.java
package exceptions;

import models.ErrorCode;

public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

// store/AgentStore.java
package store;

import models.AgentRating;
import java.util.*;

public class AgentStore {
    private static class AgentStats {
        int totalScore = 0;
        int ratingCount = 0;

        double getAverage() {
            return ratingCount == 0 ? 0.0 : (double) totalScore / ratingCount;
        }
    }

    private final Map<String, AgentStats> ratingMap = new HashMap<>();
    private final TreeSet<AgentRating> sortedRatings = new TreeSet<>();

    public synchronized void rateAgent(String agentId, int rating) {
        AgentStats stats = ratingMap.getOrDefault(agentId, new AgentStats());

        if (ratingMap.containsKey(agentId)) {
            sortedRatings.remove(new AgentRating(agentId, stats.getAverage(), stats.ratingCount));
        }

        stats.totalScore += rating;
        stats.ratingCount++;
        ratingMap.put(agentId, stats);

        sortedRatings.add(new AgentRating(agentId, stats.getAverage(), stats.ratingCount));
    }

    public synchronized List<AgentRating> getTopKAgents(int k) {
        List<AgentRating> result = new ArrayList<>();
        Iterator<AgentRating> iterator = sortedRatings.iterator();
        while (iterator.hasNext() && result.size() < k) {
            result.add(iterator.next());
        }
        return result;
    }
}

// service/AgentRatingService.java
package service;

import exceptions.ServiceException;
import models.AgentRating;
import models.ErrorCode;
import store.AgentStore;

import java.util.List;

public class AgentRatingService {
    private final AgentStore store = new AgentStore();

    public void rateAgent(String agentId, int rating) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new ServiceException(ErrorCode.INVALID_AGENT_ID, "Agent ID cannot be null or empty");
        }
        if (rating < 1 || rating > 5) {
            throw new ServiceException(ErrorCode.INVALID_RATING, "Rating must be between 1 and 5");
        }
        store.rateAgent(agentId, rating);
    }

    public List<AgentRating> getTopKAgents(int k) {
        if (k <= 0) {
            throw new ServiceException(ErrorCode.INVALID_TOP_K_REQUEST, "Top K value must be greater than 0");
        }
        return store.getTopKAgents(k);
    }
}

// Main.java
import models.AgentRating;
import service.AgentRatingService;
import exceptions.ServiceException;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        AgentRatingService service = new AgentRatingService();

        try {
            service.rateAgent("A1", 5);
            service.rateAgent("A2", 4);
            service.rateAgent("A1", 3);
            service.rateAgent("A3", 5);
            service.rateAgent("A3", 5);
            service.rateAgent("A2", 5);

            List<AgentRating> topAgents = service.getTopKAgents(2);
            topAgents.forEach(System.out::println);

            service.rateAgent("", 5); // Should throw
        } catch (ServiceException e) {
            System.out.println("Error: " + e.getMessage() + " [" + e.getErrorCode() + "]");
        }
    }
}
