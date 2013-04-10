package org.ala.biocache.web;
import org.ala.biocache.dao.SearchDAO;
import org.ala.biocache.dto.SpatialSearchRequestParams;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.response.FieldStatsInfo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import au.org.ala.biocache.*;
import au.org.ala.util.DuplicateRecordDetails;

import java.util.Map;

import javax.inject.Inject;

@Controller
public class DuplicationController {

  /** Logger initialisation */
  private final static Logger logger = Logger.getLogger(DuplicationController.class);
  /** Fulltext search DAO */
  @Inject
  protected SearchDAO searchDAO;
  
  
    /**
     * Retrieves the duplication information for the supplied guid.
     * 
     * Returns empty details when the record is not the "representative" occurrence.
     * @param guid
     * @return
     * @throws Exception
     */
    @RequestMapping(value={"/duplicates/{guid:.+}.json*","/duplicates/{guid:.+}*" })
    public @ResponseBody DuplicateRecordDetails getDuplicateStats(@PathVariable("guid") String guid) throws Exception {
      try{
          return Store.getDuplicateDetails(guid);
      }
      catch(Exception e){
          logger.error("Unable to get duplicate details for " + guid, e);
          return new DuplicateRecordDetails();
      }
    }

    @RequestMapping(value={"/stats/{guid:.+}.json*","/stats/{guid:.+}*" })
    public @ResponseBody Map<String, FieldStatsInfo> printStats(@PathVariable("guid") String guid) throws Exception {
        SpatialSearchRequestParams searchParams = new SpatialSearchRequestParams();
        searchParams.setQ("*:*");
        searchParams.setFacets(new String[]{guid});
        return searchDAO.getStatistics(searchParams);
    }
    
    
}