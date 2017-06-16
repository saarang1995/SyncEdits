package com.example.saarangt.syncedits;

import android.Manifest;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.concurrent.ListenableFuture;

import com.esri.arcgisruntime.data.Geodatabase;
import com.esri.arcgisruntime.data.GeodatabaseFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.ReverseGeocodeParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseJob;
import com.esri.arcgisruntime.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.geodatabase.GeodatabaseSyncTask;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    // Services pattern offline map
    Envelope envelope;
    ArcGISMap map;
    private MapView mMapView;
    GeodatabaseSyncTask gdbSyncTask;
    String outGeodatabasePath = "/storage/emulated/0/downloaded.geodatabase";
    Geodatabase geodatabase;
    Geodatabase gdbfinal;
    ProgressDialog progress;
    private static final String TAG = "OfflineActivity";
    final int requestCode = 2;
    final String[] permission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private GraphicsOverlay graphicsOverlay;
    private GeocodeParameters mGeocodeParameters;
    private PictureMarkerSymbol mPinSourceSymbol;
    private final String extern = "/storage/emulated/0/geocode/san-diego-locator.loc";

    private LocatorTask mLocatorTask;

    private SearchView mSearchview;
    private String mGraphicPointAddress;
    private Point mGraphicPoint;
    private GeocodeResult mGeocodedLocation;
    Spinner mSpinner;
    private TextView mCalloutContent;
    Button LoadData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progress = new ProgressDialog(this);

        // spatialReference.create(4326);
        mMapView = (MapView) findViewById(R.id.mapView);
        map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 37.7576793, -122.5076397, 5);
        mMapView.setMap(map);

      createSyncTaskAndParameters();
        setUpOfflineMapGeocoding();
        setSearchView();

    }


    void createSyncTaskAndParameters() {

        // create a new GeodatabaseSyncTask to create a local version of feature service data, passing in the service url

        String featureServiceUri = "http://sampleserver6.arcgisonline.com/arcgis/rest/services/Sync/SaveTheBaySync/FeatureServer";

        gdbSyncTask = new GeodatabaseSyncTask(featureServiceUri);

        envelope = new Envelope(-120.71677736106815, 35.11649436783033, -120.27705296719071, 35.3434949477, map.getSpatialReference());

        final ListenableFuture<GenerateGeodatabaseParameters> paramsFuture =
                gdbSyncTask.createDefaultGenerateGeodatabaseParametersAsync(envelope);

        paramsFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // get default parameters
                    GenerateGeodatabaseParameters generateGeodatabaseParameters = paramsFuture.get();

                    // make any changes required to the parameters, for example do not return attachments
                    generateGeodatabaseParameters.setReturnAttachments(false);

                    // optionally, specify the spatial reference of the geodatabase to be generated
                    generateGeodatabaseParameters.setOutSpatialReference(map.getSpatialReference());

                    // call a function to generate the geodatabase
                    generateGeodatabase(generateGeodatabaseParameters);
                } catch (InterruptedException | ExecutionException e) {
                    // dealWithException(e);
                }
            }
        });
    }

    void generateGeodatabase(final GenerateGeodatabaseParameters parameters) {
        //    parameters.getLayerOptions();

        // create the generate geodatabase job, pass in the parameters and an output path for the local geodatabase
        final GenerateGeodatabaseJob generateGeodatabaseJob = gdbSyncTask.generateGeodatabaseAsync(parameters, outGeodatabasePath);


        // start the job to generate and download the geodatabase
        generateGeodatabaseJob.start();
        progress.setTitle("Downloading feature services");
        progress.setMessage("Wait while loading...");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();

        // add a job done listener to deal with job completion - success or failure
        generateGeodatabaseJob.addJobDoneListener(new Runnable() {
            @Override
            public void run() {
                if (generateGeodatabaseJob.getStatus() == Job.Status.FAILED) {
                    progress.dismiss();
                    Toast.makeText(com.example.saarangt.syncedits.MainActivity.this, "Geodatabase generation failed", Toast.LENGTH_LONG).show();

                    // deal with job failure - check the error details on the job
                    //  dealWithJobDoneFailed(generateGeodatabaseJob.getError());
                    return;
                } else if (generateGeodatabaseJob.getStatus() == Job.Status.SUCCEEDED) {
                    progress.dismiss();
                    Toast.makeText(com.example.saarangt.syncedits.MainActivity.this, "Geodatabase generated", Toast.LENGTH_LONG).show();
                    // if the job succeeded, the geodatabase is now available at the given local path.
                    // add local data from the geodatabase to the map - see following section...
                    geodatabase = generateGeodatabaseJob.getResult();
                    try {
                        addGeodatabaseLayerToMap(geodatabase);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }


    public void addGeodatabaseLayerToMap(Geodatabase geodatabase1) throws InterruptedException {
        geodatabase1.loadAsync();

        gdbfinal = geodatabase1;

        geodatabase1.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                List<GeodatabaseFeatureTable> geodatabaseFeatureTables = gdbfinal.getGeodatabaseFeatureTables();
                for (int i = 0; i < geodatabaseFeatureTables.size(); i++) {
                //    geodatabaseFeatureTables.get(i).loadAsync();
                    mMapView.getMap().getOperationalLayers().add(i,new FeatureLayer(geodatabaseFeatureTables.get(i)));
                }
            }
        });

    }







    /////////////////////////////////////////////////////////////////////////////////////
    private void setUpOfflineMapGeocoding() {


        // add a graphics overlay
        graphicsOverlay = new GraphicsOverlay();
        graphicsOverlay.setSelectionColor(0xFF00FFFF);
        mMapView.getGraphicsOverlays().add(graphicsOverlay);


        mGeocodeParameters = new GeocodeParameters();
        mGeocodeParameters.getResultAttributeNames().add("*");
        mGeocodeParameters.setMaxResults(1);

        //[DocRef: Name=Picture Marker Symbol Drawable-android, Category=Fundamentals, Topic=Symbols and Renderers]
        //Create a picture marker symbol from an app resource
        BitmapDrawable startDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        mPinSourceSymbol = new PictureMarkerSymbol(startDrawable);
        mPinSourceSymbol.setHeight(90);
        mPinSourceSymbol.setWidth(20);
        mPinSourceSymbol.loadAsync();
        mPinSourceSymbol.setLeaderOffsetY(45);
        mPinSourceSymbol.setOffsetY(-48);


        mLocatorTask = new LocatorTask(extern);

        mCalloutContent = new TextView(getApplicationContext());
        mCalloutContent.setTextColor(Color.BLACK);
        mCalloutContent.setTextIsSelectable(true);
    }

    public void setSearchView() {


        mSearchview = (SearchView) findViewById(R.id.searchView1);
        mSearchview.setIconifiedByDefault(true);
        mSearchview.setQueryHint(getResources().getString(R.string.search_hint));
        mSearchview.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                geoCodeTypedAddress(query);
                mSearchview.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        mSpinner = (Spinner) findViewById(R.id.spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_dropdown_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                View v = super.getView(position, convertView, parent);
                if (position == getCount()) {
                    mSearchview.clearFocus();
                }

                return v;
            }

            @Override
            public int getCount() {
                return super.getCount() - 1; // you dont display last item. It is used as hint.
            }

        };

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(getResources().getStringArray(R.array.suggestion_items));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // set vertical offset to spinner dropdown for API less than 21
            mSpinner.setDropDownVerticalOffset(80);
        }
        // Apply the adapter to the spinner
        mSpinner.setAdapter(adapter);
        mSpinner.setSelection(adapter.getCount());


        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == adapter.getCount()) {
                    mSearchview.clearFocus();
                } else {

                    mSearchview.setQuery(getResources().getStringArray(R.array.suggestion_items)[position], false);
                    geoCodeTypedAddress(getResources().getStringArray(R.array.suggestion_items)[position]);
                    mSearchview.setIconified(false);
                    mSearchview.clearFocus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }


    private void geoCodeTypedAddress(final String address) {
        // Null out any previously located result
        mGeocodedLocation = null;

        // Execute async task to find the address
        mLocatorTask.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                    // Call geocodeAsync passing in an address
                    final ListenableFuture<List<GeocodeResult>> geocodeFuture = mLocatorTask.geocodeAsync(address,
                            mGeocodeParameters);
                    geocodeFuture.addDoneListener(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Get the results of the async operation
                                List<GeocodeResult> geocodeResults = geocodeFuture.get();

                                if (geocodeResults.size() > 0) {
                                    // Use the first result - for example
                                    // display on the map
                                    mGeocodedLocation = geocodeResults.get(0);
                                    displaySearchResult(mGeocodedLocation.getDisplayLocation(), mGeocodedLocation.getLabel());

                                } else {
                                    Toast.makeText(getApplicationContext(),
                                            "Location not found" + address,
                                            Toast.LENGTH_LONG).show();
                                }

                            } catch (InterruptedException | ExecutionException e) {
                                // Deal with exception...
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(),
                                        e.getMessage(),
                                        Toast.LENGTH_LONG).show();

                            }
                            // Done processing and can remove this listener.
                            geocodeFuture.removeDoneListener(this);
                        }
                    });

                } else {
                    Log.i(TAG, "Trying to reload locator task");
                    mLocatorTask.retryLoadAsync();
                }
            }
        });
        mLocatorTask.loadAsync();
    }


    private void displaySearchResult(Point resultPoint, String address) {


        if (mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }
        //remove any previous graphics/search results
        //mMapView.getGraphicsOverlays().clear();
        graphicsOverlay.getGraphics().clear();
        // create graphic object for resulting location
        Graphic resultLocGraphic = new Graphic(resultPoint, mPinSourceSymbol);
        // add graphic to location layer
        graphicsOverlay.getGraphics().add(resultLocGraphic);

        // Zoom map to geocode result location
        mMapView.setViewpointAsync(new Viewpoint(resultPoint, 8000), 3);

        mGraphicPoint = resultPoint;
        mGraphicPointAddress = address;
    }
    @Override
    protected void onPause() {
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }


}