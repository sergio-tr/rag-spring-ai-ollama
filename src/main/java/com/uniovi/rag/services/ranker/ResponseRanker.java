package com.uniovi.rag.services.ranker;

import com.uniovi.rag.model.CandidateResponse;
import com.uniovi.rag.model.RankerResult;

import java.util.List;

/**
 * Selects the best response from one or more candidates given the query and context.
 */
public interface ResponseRanker {

    /**
     * @param query     original user query
     * @param context   context used for generation (e.g. retrieved docs)
     * @param candidates list of candidate responses to rank
     * @return the chosen response and optional scores
     */
    RankerResult selectBest(String query, String context, List<CandidateResponse> candidates);
}
