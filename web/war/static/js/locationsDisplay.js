function toDegree(radian) {
  return (radian * 180) / Math.PI;
}

OpenLayers.Popup.FramedCloud.prototype.autoSize = false;

AutoSizeFramedCloud = OpenLayers.Class(OpenLayers.Popup.FramedCloud, {
    'autoSize': true
});

function transformLongLat(longlat) {
  return longlat.transform(
    new OpenLayers.Projection("EPSG:4326"),
    new OpenLayers.Projection("EPSG:900913")
  );
}

function createMarker(user, loc, map, markerLayer, icon) {
  var lat = toDegree(loc.lat);
  var long = toDegree(loc.long);
  var ts = loc.ts;
  var tsmoment = moment(Number(ts));
  var day = tsmoment.format("dddd, MMMM Do YYYY, h:mm:ss a");
  var acc = loc.acc;
  var longlat = transformLongLat(new OpenLayers.LonLat(long, lat))
  var popupClass = AutoSizeFramedCloud;
  if(user.length == 0) {
    user = "Self";
  }
  var popupContentHTML = "User: " + user + " <br />" +
  "Longitude: " + long + " <br />" +
  "Latitude: " + lat + " <br />" +
  "TimeStamp: " + day + " <br />" +
  "Accuracy: " + acc;

  var feature = new OpenLayers.Feature(markerLayer, longlat);
  feature.closeBox = false;
  feature.popupClass = popupClass;
  feature.data.icon = new OpenLayers.Icon(icon);
  feature.data.popupContentHTML = popupContentHTML;
  feature.data.overflow = (true) ? "auto" : "hidden";
  var marker = feature.createMarker();

  var markerClick = function (evt) {
      if (this.popup == null) {
          this.popup = this.createPopup(this.closeBox);
          map.addPopup(this.popup);
          this.popup.show();
      } else {
          this.popup.toggle();
      }
      currentPopup = this.popup;
      OpenLayers.Event.stop(evt);
  };
  marker.events.register("mouseover", feature, markerClick);
  marker.events.register("mouseout", feature, markerClick);
  return marker;
}

function MapView() {
  var map = new OpenLayers.Map('map');
  var layer = new OpenLayers.Layer.OSM("Simple OSM Map");
  map.addLayer(layer);
  map.addControl(new OpenLayers.Control.LayerSwitcher());
  map.setCenter(new OpenLayers.LonLat(0, 0), 000000000);
  var lineLayer, myMarkers, sharedMarkers;
  this.updateMap = function() {
    function onUpdate(serverLocations, status) {
      if (lineLayer && myMarkers && sharedMarkers) {
        map.removeLayer(lineLayer);
        map.removeLayer(myMarkers);
        map.removeLayer(sharedMarkers);
      }

      var size = new OpenLayers.Size(21, 25);
      var offset = new OpenLayers.Pixel(-(size.w / 2), -size.h);
      var icon = new OpenLayers.Icon('/static/img/marker.png', size, offset);
      var iconSelf = '/static/img/m1.png';
      var iconShared = '/static/img/m2.png';

      lineLayer = new OpenLayers.Layer.Vector("Line Layer");
      map.addLayer(lineLayer);
      myMarkers = new OpenLayers.Layer.Markers("My Location Markers");
      map.addLayer(myMarkers);
      var points = new Array();
      var obj = serverLocations;
      if(obj.locations) {
        $.each(obj.locations, function(i, loc) {
          var user = "";
          var lat = toDegree(loc.lat);
          var long = toDegree(loc.long);
          var ts = loc.ts;
          var acc = loc.acc;
          marker = createMarker(curUser,loc, map, myMarkers, iconSelf)
          myMarkers.addMarker(marker);
          var myPoints = new OpenLayers.Geometry.Point(long, lat);
          points.push(myPoints);
        });
      }

      sharedMarkers = new OpenLayers.Layer.Markers("Shared Location Markers");
      map.addLayer(sharedMarkers);
      var sharedLocations = obj.sharedLocations;
      if(sharedLocations){
        $.each(sharedLocations, function(key, value) {
          var user = key;
          sharedMarkers.addMarker(createMarker(user, value, map, sharedMarkers, iconShared));
        });
      }

      var line = new OpenLayers.Geometry.LineString(points).transform(
          new OpenLayers.Projection("EPSG:4326"),
          new OpenLayers.Projection("EPSG:900913")
        );

      var style = {
        strokeColor : '#0000ff',
        strokeOpacity : 0.5,
        strokeWidth : 3
      };

      var lineFeature = new OpenLayers.Feature.Vector(line, null, style);

      lineLayer.addFeatures([ lineFeature ]);
      map.zoomToExtent(myMarkers.getDataExtent() || sharedMarkers.getDataExtent());
    }
    $.ajax({
      url : retrieveURL, 
      success : onUpdate
    });
  }
}

$(window).load(function() {
  OpenLayers.ImgPath = "/static/img/";
  var mapView = new MapView;
  mapView.updateMap();
  $("#mapUpdate").click(mapView.updateMap);
});
