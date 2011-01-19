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

// Note there are some global variables that are set by the calling page (which has access to
// the ${pageContet} object, which are required by this file.:
//
//  var contextPath = "${pageContext.request.contextPath}";
//  var speciesPageUrl = "${speciesPageUrl}";

var map, selectControl, selectFeature, marker, circle, markerInfowindow, lastInfoWindow;
var points = [];
var infoWindows = [];
var geocoder;

//var proj900913 = new OpenLayers.Projection("EPSG:900913");
//var proj4326 = new OpenLayers.Projection("EPSG:4326");

// pointer fn
function initialize() {
    loadMap();
}
/**
 * Google map API v3
 */
function loadMap() {
    var latLng = new google.maps.LatLng($('#latitude').val(), $('#longitude').val());
    map = new google.maps.Map(document.getElementById('mapCanvas'), {
        zoom: zoom,
        center: latLng,
        mapTypeControl: true,
        mapTypeControlOptions: {
            style: google.maps.MapTypeControlStyle.DROPDOWN_MENU
        },
        navigationControl: true,
        navigationControlOptions: {
            style: google.maps.NavigationControlStyle.SMALL // DEFAULT
        },
        mapTypeId: google.maps.MapTypeId.HYBRID
    });
    marker = new google.maps.Marker({
        position: latLng,
        title: 'Sighting Location',
        map: map,
        draggable: true
    });

    markerInfowindow = new google.maps.InfoWindow({
        content: '<div class="infoWindow">marker address</div>' // gets updated by geocodePosition()
    });
    
    google.maps.event.addListener(marker, 'click', function(event) {
            if (lastInfoWindow) lastInfoWindow.close();
            markerInfowindow.setPosition(event.latLng);
            markerInfowindow.open(map, marker);
            lastInfoWindow = markerInfowindow;
    });

    // Add a Circle overlay to the map.
    var radius = parseInt($('select#radius').val()) * 1000;
    circle = new google.maps.Circle({
        map: map,
        radius: radius,
        strokeWeight: 1,
        strokeColor: 'white',
        strokeOpacity: 0.5,
        fillColor: '#222', // '#2C48A6'
        fillOpacity: 0.2,
        zIndex: -10
    });
    // bind circle to marker
    circle.bindTo('center', marker, 'position');

    // Update current position info.
    //updateMarkerPosition(latLng);
    geocodePosition(latLng);

    // Add dragging event listeners.
    google.maps.event.addListener(marker, 'dragstart', function() {
        updateMarkerAddress('Dragging...');
    });

    google.maps.event.addListener(marker, 'drag', function() {
        updateMarkerAddress('Dragging...');
        //updateMarkerPosition(marker.getPosition());
    });

    google.maps.event.addListener(marker, 'dragend', function() {
        updateMarkerAddress('Drag ended');
        updateMarkerPosition(marker.getPosition());
        geocodePosition(marker.getPosition());
        LoadTaxaGroupCounts();
        map.panTo(marker.getPosition());
    });
    
    google.maps.event.addListener(map, 'zoom_changed', function() {
        //loadRecordsLayer();
    });
    
    if (!points || points.length == 0) {
        //$('#taxa-level-0 tbody td:first').click(); // click on "all species" group
        loadRecordsLayer();
    }
}

/**
 * Google geocode function
 */
function geocodePosition(pos) {
    geocoder.geocode({
        latLng: pos
    }, function(responses) {
        if (responses && responses.length > 0) {
            //console.log("geocoded position", responses[0]);
            var address = responses[0].formatted_address;
            updateMarkerAddress(address);
            // update the info window for marker icon
            var content = '<div class="infoWindow"><b>Your Location:</b><br/>'+address+'</div>';
            markerInfowindow.setContent(content);
        } else {
            updateMarkerAddress('Cannot determine address at this location.');
        }
    });
}

/**
 * Update the "address" hidden input and display span
 */
 function updateMarkerAddress(str) {
    $('#markerAddress').empty().html(str);
    $('#location').val(str);
    $('#dialog-confirm code').html(str); // download popup text
}

/**
 * Update the lat & lon hidden input elements
 */
function updateMarkerPosition(latLng) {
    $('#latitude').val(latLng.lat());
    $('#longitude').val(latLng.lng());
    // Update URL hash for back button, etc
    location.hash = latLng.lat() + "|" + latLng.lng() + "|" + zoom;
    $('#dialog-confirm #rad').html(radius);
}

/**
 * Load (reload) geoJSON data into vector layer
 */
function loadRecordsLayer(retry) {
    if (!map && !retry) {
        // in case AJAX calls this function before map has initialised
        setTimeout(function() {if (!points || points.length == 0) {loadRecordsLayer(true)}}, 2000);
        return;
    } else if (!map) {
        //console.log('retry failed');
        return;
    }
  
    // URL for GeoJSON web service
    var geoJsonUrl = contextPath + "/geojson/radius-points";
    var zoom = (map && map.getZoom()) ? map.getZoom() : 12;
    // request params for ajax geojson call
    var params = {
        "taxa": taxa,
        "rank": rank,
        "lat": $('#latitude').val(),
        "long": $('#longitude').val(),
        "radius": $('#radius').val(),
        "zoom": zoom
    };
    //console.log('About to call $.get', map);
    // JQuery AJAX call
    $.get(geoJsonUrl, params, loadNewGeoJsonData);
}

/**
 * Callback for geoJSON ajax call
 */
function loadNewGeoJsonData(data) {
    // clear vector featers and popups
    if (points && points.length > 0) {
        $.each(points, function (i, p) {
            p.setMap(null); // remove from map
        });
        points = [];
    } else {
        points = [];
    }

    if (infoWindows && infoWindows.length > 0) {
        $.each(infoWindows, function (i, n) {
            n.close(); // close any open popups
        });
        infoWindows = [];
    } else {
        infoWindows = [];
    }

    $.each(data.features, function (i, n) {
        var latLng1 = new google.maps.LatLng(n.geometry.coordinates[1], n.geometry.coordinates[0]);
        var iconUrl = contextPath+"/static/images/circle-"+n.properties.color.replace('#','')+".png";
        var markerImage = new google.maps.MarkerImage(iconUrl,
            new google.maps.Size(9, 9),
            new google.maps.Point(0,0),
            new google.maps.Point(4, 5)
        );
        points[i] = new google.maps.Marker({
            map: map,
            position: latLng1,
            title: n.properties.count+" occurrences",
            icon: markerImage
        });

        var solrQuery;
        if (taxa.indexOf("|") > 0) {
            var parts = taxa.split("|");
            var newParts = [];
            for (j in parts) {
                newParts.push(rank+":"+parts[j]);
            }
            solrQuery = newParts.join(" OR ");
        } else {
            solrQuery = rank+':'+taxa;
        }

        var content = '<div class="infoWindow">Number of records: '+n.properties.count+'<br/>'+
                '<a href="'+ contextPath +'/occurrences/searchByArea?q='+solrQuery+'|'+
                n.geometry.coordinates[1]+'|'+n.geometry.coordinates[0]+'|0.05">View list of records</a></div>';
        infoWindows[i] = new google.maps.InfoWindow({
            content: content,
            maxWidth: 200,
            disableAutoPan: false
        });
        google.maps.event.addListener(points[i], 'click', function(event) {
            if (lastInfoWindow) lastInfoWindow.close(); // close any previously opened infoWindow
            infoWindows[i].setPosition(event.latLng);
            infoWindows[i].open(map, points[i]);
            lastInfoWindow = infoWindows[i]; // keep reference to current infoWindow
        });
    });
    
}

/**
 * Try to get a lat/long using HTML5 geoloation API
 */
function attemptGeolocation() {
    // HTML5 GeoLocation
    if (navigator && navigator.geolocation) {
        //console.log("trying to get coords with navigator.geolocation...");  
        function getMyPostion(position) {  
            //alert('coords: '+position.coords.latitude+','+position.coords.longitude);
            //console.log('geolocation request accepted');
            $('#mapCanvas').empty();
            updateMarkerPosition(new google.maps.LatLng(position.coords.latitude, position.coords.longitude));
            LoadTaxaGroupCounts();
            initialize();
        }
        
        function positionWasDeclined() {
            //console.log('geolocation request declined or errored');
            $('#mapCanvas').empty();
            //zoom = 12;
            //alert('latitude = '+$('#latitude').val());
            updateMarkerPosition(new google.maps.LatLng($('#latitude').val(), $('#longitude').val()));
            LoadTaxaGroupCounts();
            initialize();
        }
        // Add message to browser - FF needs this as it is not easy to see
        var msg = 'Waiting for confirmation to use your current location (see browser message at top of window)'+
            '<br/><a href="#" onClick="loadMap(); return false;">Click here to load map</a>';
        $('#mapCanvas').html(msg).css('color','red').css('font-size','14px');
        navigator.geolocation.getCurrentPosition(getMyPostion, positionWasDeclined);
        //console.log("line after navigator.geolocation.getCurrentPosition...");  
        // Neither functions gets called for some reason, so I've added a delay to initalize map anyway
        setTimeout(function() {if (!map) positionWasDeclined();}, 9000);
    } else if (google.loader && google.loader.ClientLocation) {
        // Google AJAX API fallback GeoLocation
        //alert("getting coords using google geolocation");
        updateMarkerPosition(new google.maps.LatLng(google.loader.ClientLocation.latitude, google.loader.ClientLocation.longitude));
        LoadTaxaGroupCounts();
        initialize();
    } else {
        //alert("Client geolocation failed");
        //codeAddress();
        zoom = 12;
        initialize();
    }
}

/**
 * Reverse geocode coordinates via Google Maps API
 */
function codeAddress(reverseGeocode) {
    var address = $('input#address').val();

    if (geocoder && address) {
        //geocoder.getLocations(address, addAddressToPage);
        geocoder.geocode( {'address': address, region: 'AU'}, function(results, status) {
            if (status == google.maps.GeocoderStatus.OK) {
                // geocode was successful
                updateMarkerAddress(results[0].formatted_address);
                updateMarkerPosition(results[0].geometry.location);
                // reload map pin, etc
                initialize();
                loadRecordsLayer();
                LoadTaxaGroupCounts();
            } else {
                alert("Geocode was not successful for the following reason: " + status);
            }
        });
    } else {
        initialize();
    }
}

/**
 * Geocode location via Google Maps API
 */
function addAddressToPage(response) {
    //map.clearOverlays();
    if (!response || response.Status.code != 200) {
        alert("Sorry, we were unable to geocode that address");
    } else {
        var location = response.Placemark[0];
        var lat = location.Point.coordinates[1]
        var lon = location.Point.coordinates[0];
        var locationStr = response.Placemark[0].address;
        updateMarkerAddress(locationStr);
        updateMarkerPosition(new google.maps.LatLng(lat, lon));
    }
}

/**
 * Process the JSON data from an Species list AJAX request (species in area)
 */
function processSpeciesJsonData(data, appendResults) {
    // clear right list unless we're paging
    if (!appendResults) {
        //$('#loadMoreSpecies').detach();
        $('#rightList tbody').empty();
    }
    // process JSON data
    if (data.speciesCount > 0) {
        var lastRow = $('#rightList tbody tr').length;
        var linkTitle = "display on map";
        var infoTitle = "view species page";
        var recsTitle = "view list of records";
        // iterate over list of species from search
        for (i=0;i<data.species.length;i++) {
            // create new table row
            var count = i + lastRow;
            // add count
            var tr = '<tr><td>'+(count+1)+'.</td>';
            // add scientific name
            tr = tr + '<td class="sciName"><a id="taxon_name" class="taxonBrowse2" title="'+linkTitle+'" href="'+
                data.species[i].name+'"><i>'+data.species[i].name+'</i></a>';
            // add common name
            if (data.species[i].commonName) {
                tr = tr + ' ('+data.species[i].commonName+')';
            }
            // add links to species page and ocurrence search (inside hidden div)
            var speciesInfo = '<div class="speciesInfo">';
            if (data.species[i].guid) {
                speciesInfo = speciesInfo + '<a title="'+infoTitle+'" href="'+speciesPageUrl + data.species[i].guid+
                    '"><img src="'+ contextPath +'/static/css/images/page_white_go.png" alt="species page icon" style="margin-bottom:-3px;" class="no-rounding"/>'+
                    ' species profile</a> | ';
            }
            speciesInfo = speciesInfo + '<a href="'+ contextPath +'/occurrences/searchByArea?q=taxon_name:'+data.species[i].name+
                    '|'+$('input#latitude').val()+'|'+$('input#longitude').val()+'|'+$('select#radius').val()+'" title="'+
                    recsTitle+'"><img src="'+ contextPath +'/static/css/images/database_go.png" '+
                    'alt="search list icon" style="margin-bottom:-3px;" class="no-rounding"/> list of records</a></div>';
            tr = tr + speciesInfo;
            // add number of records
            tr = tr + '</td><td class="rightCounts">'+data.species[i].count+' </td></tr>';
            // write list item to page
            $('#rightList tbody').append(tr);
            //if (console) console.log("tr = "+tr);
        }

        if (data.species.length == 50) {
            // add load more link
            var newStart = $('#rightList tbody tr').length;
            $('#rightList tbody').append('<tr id="loadMoreSpecies"><td>&nbsp;</td><td colspan="2"><a href="'+newStart+
                '">Show more species</a></td></tr>');
        }

    } else if (appendResults) {
        // do nothing
    } else {
        // no spceies were found (either via paging or clicking on taxon group
        var text = '<tr><td></td><td colspan="2">[no species found]</td></tr>';
        $('#rightList tbody').append(text);
    }

    // Register clicks for the list of species links so that map changes
    $('#rightList tbody tr').click(function(e) {
        e.preventDefault(); // ignore the href text - used for data
        var taxon = $(this).find('a.taxonBrowse2').attr('href');
        rank = $(this).find('a.taxonBrowse2').attr('id');
        taxa = []; // array of taxa
        taxa = (taxon.indexOf("|") > 0) ? taxon.split("|") : taxon;
        //$(this).unbind('click'); // activate links inside this row
        $('#rightList tbody tr').removeClass("activeRow2"); // un-highlight previous current taxon
        // remove previous species info row
        $('#rightList tbody tr#info').detach();
        var info = $(this).find('.speciesInfo').html();
        // copy contents of species into a new (tmp) row
        if (info) {
            $(this).after('<tr id="info"><td><td>'+info+'<td></td></tr>');
        }
        // hide previous selected spceies info box
        $(this).addClass("activeRow2"); // highloght current taxon
        // show the links for current selected species
        //console.log('species link -> loadRecordsLayer()');
        loadRecordsLayer();
    });

    // Register onClick for "load more species" link
    $('#loadMoreSpecies a').click(
        function(e) {
            e.preventDefault(); // ignore the href text - used for data
            var taxon = $('#taxa-level-0 tr.activeRow').find('a.taxonBrowse').attr('href');
            rank = $('#taxa-level-0 tr.activeRow').find('a.taxonBrowse').attr('id');
            taxa = []; // array of taxa
            taxa = (taxon.indexOf("|") > 0) ? taxon.split("|") : taxon;
            var start = $(this).attr('href');
            // AJAX...
            var uri = contextPath +"/explore/species.json";
            var params = "?latitude="+$('#latitude').val()+"&longitude="+$('#longitude').val()+"&radius="+$('#radius').val()+"&taxa="+taxa+"&rank="+rank+"&start="+start;
            //$('#taxaDiv').html('[loading...]');
            $('#loadMoreSpecies').detach(); // delete it
            $.getJSON(uri + params, function(data) {
                // process JSON data from request
                processSpeciesJsonData(data, true);
            });
        }
    );

    // add hover effect to table cell with scientific names
    $('#rightList tbody tr').hover(
        function() {
            $(this).addClass('hoverCell');
        },
        function() {
            $(this).removeClass('hoverCell');
        }
    );
}

/**
 * For each taxa group get species counts via AJAX call
 */
function LoadTaxaGroupCounts() {
    $('a.taxonBrowse').each(function(index) {
        var countsUrl = contextPath +"/explore/taxaGroupCount";
        var element = $(this); // for use inside ajax callback
        var params = {
            "group": $(this).attr('title'),
            "latitude": $('#latitude').val(),
            "longitude": $('#longitude').val(),
            "radius": $('#radius').val()
        }
        $.get(countsUrl, params, function(count) {
            $(element).parent('td').siblings(':last-child').html(count);
        });
    });
    // reload the all species right list
    $('#taxa-level-0 tbody td:first').click();
}