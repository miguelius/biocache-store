/*
 *  Copyright (C) 2010 Atlas of Living Australia
 *  All Rights Reserved.
 * 
 *  The contents of this file are subject to the Mozilla Public
 *  License Version 1.1 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of
 *  the License at http://www.mozilla.org/MPL/
 * 
 *  Software distributed under the License is distributed on an "AS
 *  IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  rights and limitations under the License.
 */

package org.ala.biocache.model;

import java.io.Serializable;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;

/**
 * Occurrence DTO bean to be populated by SOLR query via SOLRJ
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 */
public class OccurrenceDTO implements Serializable {
    /*
     * Fields corresponding to indexed fields in SOLR
     */
    @Field private Integer id;
    // dataset
	@Field("data_provider_id") private Integer dataProviderId;
	@Field("data_provider") private String dataProvider;
	@Field("data_resource_id") private Integer dataResourceId;
	@Field("data_resource") private String dataResource;
	@Field("institution_code_id") private Integer institutionCodeId;
	@Field("institution_code") private String institutionCode;
	@Field("institution_code_name") private String institutionCodeName;
	@Field("institution_code_lsid") private String institutionCodeLsid;
	@Field("collection_code_id") private Integer collectionCodeId;
	@Field("collection_code") private String collectionCode;
	@Field("catalogue_number_id") private Integer catalogueNumberId;
	@Field("catalogue_number") private String catalogueNumber;
	@Field("taxon_concept_lsid") private String taxonConceptLsid;
	@Field private String year;
	@Field private String month;
	@Field("basis_of_record_id") private Integer basisOfRecordId;
	@Field("basis_of_record") private String basisOfRecord;
	@Field("raw_basis_of_record") private String rawBasisOfRecord;
	@Field private String collector;
	@Field("type_status") private String typeStatus;
	@Field("identifier_type") private String identifierType;
	@Field("identifier_value") private String identifierValue;
	@Field("identifier_name") private String identifierName;
	@Field("identifier_date") private String identifierDate;
    // taxonomy
	@Field("taxon_name") private String taxonName;
	@Field private String author;
	@Field("raw_taxon_name") private String rawTaxonName;
	@Field("raw_author") private String rawAuthor;
	@Field("kingdom_lsid") private String kingdomLsid;
	@Field private String kingdom;
    @Field("phylum_lsid") private String phylumLsid;
	@Field private String phylum;
    @Field("class_lsid") private String classLsid;
	@Field private String clazz;
    @Field("order_lsid") private String orderLsid;
	@Field private String order;
	@Field("family_lsid") private String familyLsid;
	@Field private String family;
    @Field("genus_lsid") private String genusLsid;
	@Field private String genus;
    @Field("species_lsid") private String speciesLsid;
	@Field private String species;
    // geospatial
	@Field("country_code") private String countryCode;
	@Field private List<String> state;
	@Field("biogeographic_region") private List<String> biogeographicRegion;
	@Field private List<String> places;
	@Field private Float latitude;
	@Field private Float longitude;
	@Field("cell_id") private Integer cellId;
	@Field("centi_cell_id") private Integer centiCellId;
	@Field("tenmilli_cell_id") private Integer tenmilliCellId;
    // other
	@Field("taxonomic_issue") private String taxonomicIssue;
	@Field("geospatial_issue") private String geospatialIssue;
	@Field("other_issue") private String otherIssue;
	@Field("modified_date") private String modifiedDate;

    /*
     * Getters & Setters
     */
    
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBasisOfRecord() {
        return basisOfRecord;
    }

    public void setBasisOfRecord(String basisOfRecord) {
        this.basisOfRecord = basisOfRecord;
    }

    public Integer getBasisOfRecordId() {
        return basisOfRecordId;
    }

    public void setBasisOfRecordId(Integer basisOfRecordId) {
        this.basisOfRecordId = basisOfRecordId;
    }

    public List<String> getBiogeographicRegion() {
        return biogeographicRegion;
    }

    public void setBiogeographicRegion(List<String> biogeographicRegion) {
        this.biogeographicRegion = biogeographicRegion;
    }

    public String getCatalogueNumber() {
        return catalogueNumber;
    }

    public void setCatalogueNumber(String catalogueNumber) {
        this.catalogueNumber = catalogueNumber;
    }

    public Integer getCatalogueNumberId() {
        return catalogueNumberId;
    }

    public void setCatalogueNumberId(Integer catalogueNumberId) {
        this.catalogueNumberId = catalogueNumberId;
    }

    public Integer getCellId() {
        return cellId;
    }

    public void setCellId(Integer cellId) {
        this.cellId = cellId;
    }

    public Integer getCentiCellId() {
        return centiCellId;
    }

    public void setCentiCellId(Integer centiCellId) {
        this.centiCellId = centiCellId;
    }

    public String getClassLsid() {
        return classLsid;
    }

    public void setClassLsid(String classLsid) {
        this.classLsid = classLsid;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getCollectionCode() {
        return collectionCode;
    }

    public void setCollectionCode(String collectionCode) {
        this.collectionCode = collectionCode;
    }

    public Integer getCollectionCodeId() {
        return collectionCodeId;
    }

    public void setCollectionCodeId(Integer collectionCodeId) {
        this.collectionCodeId = collectionCodeId;
    }

    public String getCollector() {
        return collector;
    }

    public void setCollector(String collector) {
        this.collector = collector;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getDataProvider() {
        return dataProvider;
    }

    public void setDataProvider(String dataProvider) {
        this.dataProvider = dataProvider;
    }

    public Integer getDataProviderId() {
        return dataProviderId;
    }

    public void setDataProviderId(Integer dataProviderId) {
        this.dataProviderId = dataProviderId;
    }

    public String getDataResource() {
        return dataResource;
    }

    public void setDataResource(String dataResource) {
        this.dataResource = dataResource;
    }

    public Integer getDataResourceId() {
        return dataResourceId;
    }

    public void setDataResourceId(Integer dataResourceId) {
        this.dataResourceId = dataResourceId;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getFamilyLsid() {
        return familyLsid;
    }

    public void setFamilyLsid(String familyLsid) {
        this.familyLsid = familyLsid;
    }

    public String getGenus() {
        return genus;
    }

    public void setGenus(String genus) {
        this.genus = genus;
    }

    public String getGenusLsid() {
        return genusLsid;
    }

    public void setGenusLsid(String genusLsid) {
        this.genusLsid = genusLsid;
    }

    public String getGeospatialIssue() {
        return geospatialIssue;
    }

    public void setGeospatialIssue(String geospatialIssue) {
        this.geospatialIssue = geospatialIssue;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIdentifierDate() {
        return identifierDate;
    }

    public void setIdentifierDate(String identifierDate) {
        this.identifierDate = identifierDate;
    }

    public String getIdentifierName() {
        return identifierName;
    }

    public void setIdentifierName(String identifierName) {
        this.identifierName = identifierName;
    }

    public String getIdentifierType() {
        return identifierType;
    }

    public void setIdentifierType(String identifierType) {
        this.identifierType = identifierType;
    }

    public String getIdentifierValue() {
        return identifierValue;
    }

    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public Integer getInstitutionCodeId() {
        return institutionCodeId;
    }

    public void setInstitutionCodeId(Integer institutionCodeId) {
        this.institutionCodeId = institutionCodeId;
    }

    public String getInstitutionCodeLsid() {
        return institutionCodeLsid;
    }

    public void setInstitutionCodeLsid(String institutionCodeLsid) {
        this.institutionCodeLsid = institutionCodeLsid;
    }

    public String getInstitutionCodeName() {
        return institutionCodeName;
    }

    public void setInstitutionCodeName(String institutionCodeName) {
        this.institutionCodeName = institutionCodeName;
    }

    public String getKingdom() {
        return kingdom;
    }

    public void setKingdom(String kingdom) {
        this.kingdom = kingdom;
    }

    public String getKingdomLsid() {
        return kingdomLsid;
    }

    public void setKingdomLsid(String kingdomLsid) {
        this.kingdomLsid = kingdomLsid;
    }

    public Float getLatitude() {
        return latitude;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public Float getLongitude() {
        return longitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    public String getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(String modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getOrderLsid() {
        return orderLsid;
    }

    public void setOrderLsid(String orderLsid) {
        this.orderLsid = orderLsid;
    }

    public String getOtherIssue() {
        return otherIssue;
    }

    public void setOtherIssue(String otherIssue) {
        this.otherIssue = otherIssue;
    }

    public String getPhylum() {
        return phylum;
    }

    public void setPhylum(String phylum) {
        this.phylum = phylum;
    }

    public String getPhylumLsid() {
        return phylumLsid;
    }

    public void setPhylumLsid(String phylumLsid) {
        this.phylumLsid = phylumLsid;
    }

    public List<String> getPlaces() {
        return places;
    }

    public void setPlaces(List<String> places) {
        this.places = places;
    }

    public String getRawAuthor() {
        return rawAuthor;
    }

    public void setRawAuthor(String rawAuthor) {
        this.rawAuthor = rawAuthor;
    }

    public String getRawBasisOfRecord() {
        return rawBasisOfRecord;
    }

    public void setRawBasisOfRecord(String rawBasisOfRecord) {
        this.rawBasisOfRecord = rawBasisOfRecord;
    }

    public String getRawTaxonName() {
        return rawTaxonName;
    }

    public void setRawTaxonName(String rawTaxonName) {
        this.rawTaxonName = rawTaxonName;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getSpeciesLsid() {
        return speciesLsid;
    }

    public void setSpeciesLsid(String speciesLsid) {
        this.speciesLsid = speciesLsid;
    }

    public List<String> getState() {
        return state;
    }

    public void setState(List<String> state) {
        this.state = state;
    }

    public String getTaxonConceptLsid() {
        return taxonConceptLsid;
    }

    public void setTaxonConceptLsid(String taxonConceptLsid) {
        this.taxonConceptLsid = taxonConceptLsid;
    }

    public String getTaxonName() {
        return taxonName;
    }

    public void setTaxonName(String taxonName) {
        this.taxonName = taxonName;
    }

    public String getTaxonomicIssue() {
        return taxonomicIssue;
    }

    public void setTaxonomicIssue(String taxonomicIssue) {
        this.taxonomicIssue = taxonomicIssue;
    }

    public Integer getTenmilliCellId() {
        return tenmilliCellId;
    }

    public void setTenmilliCellId(Integer tenmilliCellId) {
        this.tenmilliCellId = tenmilliCellId;
    }

    public String getTypeStatus() {
        return typeStatus;
    }

    public void setTypeStatus(String typeStatus) {
        this.typeStatus = typeStatus;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

}
