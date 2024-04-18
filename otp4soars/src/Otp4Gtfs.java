import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.module.GtfsFeedId;
import org.opentripplanner.graph_builder.module.GtfsModule;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Otp4Gtfs {

    public Graph graph;

    public void initializeOtp4Gtfs (String pathToGtfsDir) {

        List<GtfsBundle> gtfsBundles = null;
        try (final Stream<Path> pathStream = Files.list(Paths.get(pathToGtfsDir))) {
            gtfsBundles = pathStream.map(Path::toFile).filter(file -> file.getName().toLowerCase().endsWith(".zip"))
                    .map(file -> {
                        GtfsBundle gtfsBundle = new GtfsBundle(file);
                        gtfsBundle.setTransfersTxtDefinesStationPaths(true);
                        String id = file.getName().substring(0, file.getName().length() - 4);
                        gtfsBundle.setFeedId(new GtfsFeedId.Builder().id(id).build());
                        return gtfsBundle;
                    }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        GtfsModule gtfsModule = new GtfsModule(gtfsBundles);
        HashMap<Class<?>, Object> extra = new HashMap<>();
        graph = new Graph();
        gtfsModule.buildGraph(graph, extra);
        graph.summarizeBuilderAnnotations();
        graph.index(new DefaultStreetVertexIndexFactory());
    }

    public int routingGtfs (float o_lat, float o_lon, float d_lat, float d_lon, String dateAndTime,int noOfRoutes){
        RoutingRequest routingRequest = new RoutingRequest();
        routingRequest.setNumItineraries(noOfRoutes);
        LocalDateTime ldt = LocalDateTime.parse("2024-08-01T13:00");
        routingRequest.dateTime = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()).getTime() / 1000;
        routingRequest.setArriveBy(false);
        routingRequest.from = new GenericLocation(24.390622779161614, 124.2461127409781);
        routingRequest.to = new GenericLocation(24.34506770237078, 124.16102033209339);
        routingRequest.ignoreRealtimeUpdates = true;
        routingRequest.reverseOptimizing = true;
        routingRequest.setModes(new TraverseModeSet(TraverseMode.TRANSIT));
//        routingRequest.onlyTransitTrips = false;
//        routingRequest.maxWalkDistance = 2000; // 最大徒歩距離を2kmに設定
        routingRequest.setRoutingContext(graph);


        Router router = new Router("OTP_GTFS", graph);
        List<GraphPath> paths = new GraphPathFinder(router).getPaths(routingRequest);
        TripPlan tripPlan = GraphPathToTripPlanConverter.generatePlan(paths, routingRequest);

        AtomicInteger num = new AtomicInteger(0);
        tripPlan.itinerary.forEach(p -> {
            System.out.println("-- Route " + num.incrementAndGet() + " --");
            System.out.println(String.format("%d:%02d Depart - %d:%02d Arrive (%d mins)",
                    p.startTime.get(Calendar.HOUR_OF_DAY), p.startTime.get(Calendar.MINUTE),
                    p.endTime.get(Calendar.HOUR_OF_DAY), p.endTime.get(Calendar.MINUTE),
                    (p.duration / 60)));
            p.legs.forEach(l -> {
                System.out.println(String.format("> From %s at %d:%02d to %s at %d:%02d",
                        l.from.name, l.startTime.get(Calendar.HOUR_OF_DAY), l.startTime.get(Calendar.MINUTE),
                        l.to.name, l.endTime.get(Calendar.HOUR_OF_DAY), l.endTime.get(Calendar.MINUTE)));
            });
        });
        return 0;
    }
}
