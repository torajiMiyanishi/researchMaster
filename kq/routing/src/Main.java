import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.module.GtfsFeedId;
import org.opentripplanner.graph_builder.module.GtfsModule;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.Trip;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;

public class Main {

    public static void main(String[] args) {

//        // GTFSファイルの保存フォルダのパス
//        String in = "C:\\lab\\research-master\\otp\\shimodenbus.gtfs.zip";
//
//        // フォルダ内の全ZIPファイルをGTFSファイルとして読み込む
//        List<GtfsBundle> gtfsBundles = null;
//        try (final Stream<Path> pathStream = Files.list(Paths.get(in))) {
//            gtfsBundles = pathStream.map(Path::toFile).filter(file -> file.getName().toLowerCase().endsWith(".zip"))
//                    .map(file -> {
//                        GtfsBundle gtfsBundle = new GtfsBundle(file);
//                        gtfsBundle.setTransfersTxtDefinesStationPaths(true);
//                        // 各GTFSファイルに一意のIDをつける必要があるのでファイル名をIDとする
//                        String id = file.getName().substring(0, file.getName().length() - 4);
//                        gtfsBundle.setFeedId(new GtfsFeedId.Builder().id(id).build());
//                        return gtfsBundle;
//                    }).collect(Collectors.toList());
//        } catch (final IOException e) {
//            throw new RuntimeException(e);
//        }
        // GTFSファイルのパス
        String gtfsZipFilePath = "C:\\lab\\research-master\\otp\\shimodenbus.gtfs.zip";
        File gtfsZipFile = new File(gtfsZipFilePath);

        // 単一のGTFSファイルをGTFSバンドルとして読み込む
        GtfsBundle gtfsBundle = new GtfsBundle(gtfsZipFile);
        gtfsBundle.setTransfersTxtDefinesStationPaths(true);
        // ファイル名をIDとして使用
        String id = gtfsZipFile.getName().substring(0, gtfsZipFile.getName().length() - 4);
        gtfsBundle.setFeedId(new GtfsFeedId.Builder().id(id).build());

        // GtfsBundleをリストに追加
        List<GtfsBundle> gtfsBundles = new ArrayList<>();
        gtfsBundles.add(gtfsBundle);

        // 読み込んだGTFSファイルをGraphオブジェクトに登録する処理
        GtfsModule gtfsModule = new GtfsModule(gtfsBundles);
        Graph graph = new Graph();
        gtfsModule.buildGraph(graph, null);
        graph.summarizeBuilderAnnotations();
        graph.index(new DefaultStreetVertexIndexFactory());

        // Graphはシリアライズして保存することも可能
        // graph.save(file);

        // 探索条件の設定
        RoutingRequest routingRequest = new RoutingRequest();
        routingRequest.setNumItineraries(3); // 探索結果の候補数
        // 探索する基準時間を設定
        LocalDateTime ldt = LocalDateTime.parse("2024-03-27T13:00");
        routingRequest.dateTime = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()).getTime() / 1000;
        routingRequest.setArriveBy(false); // 既定は出発時間指定だが、Trueとすると到着時間を指定できる
        // 出発地と到着地を緯度/経度で指定
        routingRequest.from = new GenericLocation(34.58609398165849, 133.76968375315076); // 辻堂駅
        routingRequest.to = new GenericLocation(34.60176158616638, 133.76717626518115); // 名古屋駅
        routingRequest.ignoreRealtimeUpdates = true; //GTFSRealtime情報を無視
        routingRequest.reverseOptimizing = true; // 探索結果から逆方向に探索し直し、最遅出発時間を探索
        // GTFSデータのみで探索を行う設定
        routingRequest.setModes(new TraverseModeSet(TraverseMode.TRANSIT));
        routingRequest.onlyTransitTrips = true;
        // Graphオブジェクトをセット
        routingRequest.setRoutingContext(graph);

        // 探索処理
        Router router = new Router("OTP", graph);
        List<GraphPath> paths = new GraphPathFinder(router).getPaths(routingRequest);
        TripPlan tripPlan = GraphPathToTripPlanConverter.generatePlan(paths, routingRequest);

        // 探索結果を表示
        tripPlan.itinerary.forEach(p -> {
            // 各経路候補のサマリー情報
            System.out.println(String.format("%d:%02d 出発 - %d:%02d 到着 (%d分)",
                    p.startTime.get(Calendar.HOUR_OF_DAY), p.startTime.get(Calendar.MINUTE),
                    p.endTime.get(Calendar.HOUR_OF_DAY), p.endTime.get(Calendar.MINUTE),
                    (p.duration / 60)));
            p.legs.forEach(l -> {
                //　経路内の各列車の情報
                System.out.println(String.format("> %s %d:%02d 発 - %s %d:%02d 着",
                        l.from.name, l.startTime.get(Calendar.HOUR_OF_DAY), l.startTime.get(Calendar.MINUTE),
                        l.to.name, l.endTime.get(Calendar.HOUR_OF_DAY), l.endTime.get(Calendar.MINUTE)));
            });
        });
    }
}
