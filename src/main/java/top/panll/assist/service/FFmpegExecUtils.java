package top.panll.assist.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFmpegUtils;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;
import org.springframework.util.DigestUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FFmpegExecUtils {

    private static FFmpegExecUtils instance;

    public FFmpegExecUtils() {
    }

    public static FFmpegExecUtils getInstance(){
        if(instance==null){
            synchronized (FFmpegExecUtils.class){
                if(instance==null){
                    instance=new FFmpegExecUtils();
                }
            }
        }
        return instance;
    }

    public FFprobe ffprobe;
    public FFmpeg ffmpeg;

    public interface VideoHandEndCallBack {
        void run(String status, double percentage, String result);
    }

    public String mergeOrCutFile(List<File> fils, File dest, String temp, VideoHandEndCallBack callBack){
        FFmpeg ffmpeg = FFmpegExecUtils.getInstance().ffmpeg;
        FFprobe ffprobe = FFmpegExecUtils.getInstance().ffprobe;
        if (fils == null || fils.size() == 0 || ffmpeg == null || ffprobe == null || dest== null || !dest.exists()){
            callBack.run("error", 0.0, null);
            return null;
        }


        File tempFile = new File(dest.getAbsolutePath() + File.separator + temp);
        if (!tempFile.exists()) {
            tempFile.mkdirs();
        }
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        String fileListName = tempFile.getAbsolutePath() + File.separator + "fileList";
        double durationAll = 0.0;
        try {
            BufferedWriter bw =new BufferedWriter(new FileWriter(fileListName));
            for (File file : fils) {
                String[] split = file.getName().split("-");
                if (split.length != 3) continue;
                String durationStr = split[2].replace(".mp4", "");
                Double duration = Double.parseDouble(durationStr)/1000;
                System.out.println(duration);
                bw.write("file " + file.getAbsolutePath());
                bw.newLine();
                durationAll += duration;
            }
            bw.flush();
            bw.close();
            System.out.println(durationAll);
        } catch (IOException e) {
            e.printStackTrace();
            callBack.run("error", 0.0, null);
        }
        String recordFileResultPath = dest.getAbsolutePath() + File.separator + temp + File.separator + "record.mp4";
        long startTime = System.currentTimeMillis();
        FFmpegBuilder builder = new FFmpegBuilder()

                .setFormat("concat")
                .overrideOutputFiles(true)
                .setInput(fileListName) // Or filename
                .addExtraArgs("-safe", "0")
                .addOutput(recordFileResultPath)
                .setVideoCodec("copy")
                .setFormat("mp4")
                .done();


        double finalDurationAll = durationAll;
        FFmpegJob job = executor.createJob(builder, (Progress progress) -> {
            final double duration_ns = finalDurationAll * TimeUnit.SECONDS.toNanos(1);
            double percentage = progress.out_time_ns / duration_ns;

            // Print out interesting information about the progress
//            System.out.println(String.format(
//                    "[%.0f%%] status:%s frame:%d time:%s ms fps:%.0f speed:%.2fx",
//                    percentage * 100,
//                    progress.status,
//                    progress.frame,
//                    FFmpegUtils.toTimecode(progress.out_time_ns, TimeUnit.NANOSECONDS),
//                    progress.fps.doubleValue(),
//                    progress.speed
//            ));
            if (progress.status.equals(Progress.Status.END)){
                callBack.run(progress.status.name(), percentage,dest.getName() + File.separator + temp + File.separator + "record.mp4");
                System.out.println(System.currentTimeMillis() - startTime);
            }else {
                callBack.run(progress.status.name(), percentage, null);
            }
        });

        job.run();
        return temp;
    }

}
