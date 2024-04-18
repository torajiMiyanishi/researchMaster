import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.module.osm.OpenStreetMapModule;
import org.opentripplanner.openstreetmap.impl.BinaryFileBasedOpenStreetMapProviderImpl;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Otp4Osm {

    public Graph graph;

    public void initializeOtp4Osm(String pathToPbf){
        File osmFile = new File(pathToPbf);
        BinaryFileBasedOpenStreetMapProviderImpl osmProvider = new BinaryFileBasedOpenStreetMapProviderImpl();
        osmProvider.setPath(osmFile);
        SimpleOpenStreetMapContentHandler handler = new SimpleOpenStreetMapContentHandler();
        osmProvider.readOSM(handler);
        OpenStreetMapModule osmModule = new OpenStreetMapModule(Collections.singletonList(osmProvider));

        graph = new Graph();
        HashMap<Class<?>, Object> extra = new HashMap<>();
        osmModule.buildGraph(graph, extra); // ここでGraphにOSMデータを統合
        graph.index(new DefaultStreetVertexIndexFactory());
        graph.summarizeBuilderAnnotations();
    }

    public AtomicReference<List<WalkStep>> routingOsm(float o_lat, float o_lon, float d_lat, float d_lon, String dateAndTime){
        AtomicReference<List<WalkStep>> walksteps = null;

        RoutingRequest routingRequest = new RoutingRequest();
        routingRequest.setNumItineraries(1);
        LocalDateTime ldt = LocalDateTime.parse("2024-08-01T13:00");
        routingRequest.dateTime = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()).getTime() / 1000;
        routingRequest.setArriveBy(false);
        routingRequest.from = new GenericLocation(24.390622779161614, 124.2461127409781);
        routingRequest.to = new GenericLocation(24.34506770237078, 124.16102033209339);
        routingRequest.ignoreRealtimeUpdates = true;
        routingRequest.reverseOptimizing = true;
        routingRequest.setModes(new TraverseModeSet(TraverseMode.WALK));
//        routingRequest.onlyTransitTrips = false;
//        routingRequest.maxWalkDistance = 2000; // 最大徒歩距離を2kmに設定
        routingRequest.setRoutingContext(graph);

        Router router = new Router("OTP_OSM", graph);
        List<GraphPath> paths = new GraphPathFinder(router).getPaths(routingRequest);
        TripPlan tripPlan = GraphPathToTripPlanConverter.generatePlan(paths, routingRequest);
        Itinerary itinerary0 = tripPlan.itinerary.get(0);
        Leg leg0 = itinerary0.legs.get(0);
        leg0.walkSteps.forEach(ws -> {
           System.out.println(String.format("lat:%s lon:%s" ,ws.lat,ws.lon));
        });

//        AtomicInteger num = new AtomicInteger(0);
//        tripPlan.itinerary.forEach(p -> {
//            System.out.println("-- Route " + num.incrementAndGet() + " --");
//            System.out.println(String.format("%d:%02d Depart - %d:%02d Arrive (%d mins)",
//                    p.startTime.get(Calendar.HOUR_OF_DAY), p.startTime.get(Calendar.MINUTE),
//                    p.endTime.get(Calendar.HOUR_OF_DAY), p.endTime.get(Calendar.MINUTE),
//                    (p.duration / 60)));
//            p.legs.forEach(l -> {
//                System.out.println(String.format("> From %s at %d:%02d to %s at %d:%02d",
//                        l.from.name, l.startTime.get(Calendar.HOUR_OF_DAY), l.startTime.get(Calendar.MINUTE),
//                        l.to.name, l.endTime.get(Calendar.HOUR_OF_DAY), l.endTime.get(Calendar.MINUTE)));
//                walksteps.set(l.walkSteps);
//            });
//        });
        return walksteps;
    }
}
