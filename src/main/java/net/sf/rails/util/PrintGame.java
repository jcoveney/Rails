package net.sf.rails.util;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import net.sf.rails.common.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.Scene;
import javafx.embed.swing.SwingFXUtils;

public class PrintGame {
  private static final Logger log =
            LoggerFactory.getLogger(PrintGame.class);

  //TODO parse arguments here...the output directory, and the input file
  public static void main(String[] args) {
    //TODO check to make sure have two arguments

    //TODO ensure directory exists
    directory = new File(args[0]);

    //TODO ensure game file exists
    File gameFile = new File(args[1]);

    ConfigManager.initConfiguration(false);

    try {
      //TODO need to figure out how to detect if there is an error and then fail quickly...
      //  for now could make a thread that just shuts everything down after like 5 seconds, but that is gross!
      GameLoader.loadAndStartGameReturnUI(gameFile).printGameState();
    } catch (Exception e) {
      e.printStackTrace();
      log.error("Exception! Shutting it all down!");
      // This is the worst
      System.exit(1);
    }
  }

  //TODO this should be based on...literally anything else
  private static File directory = new File("/Users/jcoveney");

  public static void printRoundFacade(String string) throws IOException {
    Path writeLocation = new File(directory, "round_facade.txt").toPath();
    log.info("Print round facade [" + string + "] to file: " + writeLocation);
    Files.writeString(writeLocation, string);
  }

  public static void printGameReport(List<String> actions) throws IOException {
    Path writeLocation = new File(directory, "game_report.txt").toPath();
    log.info("Print grame report to file: " + writeLocation);
    boolean first = true;
    BufferedWriter writer = Files.newBufferedWriter(writeLocation, Charset.forName("UTF-8"));
    for (String action : actions) {
      if (first) {
        first = false;
      } else {
        writer.newLine();
      }
      writer.write(action);
    }
    writer.close();
  }

  public static void printPanel(Scene scene, String _file) throws IOException {
    File file = new File(directory, _file);
    log.info("printPanel with Scene " + scene + "sent to file" + file);
    WritableImage writableImage = scene.snapshot(null);
    ImageIO.write(SwingFXUtils.fromFXImage(writableImage, null), "png", file);
  }

  public static void printPanel(Component panel, String _file) throws IOException, AWTException {
    File file = new File(directory, _file);
    log.info("printPanel with Component " + panel + "sent to file" + file);
    //TODO should have some logging
    BufferedImage imagebuf = null;
    imagebuf = new Robot().createScreenCapture(panel.bounds());
    panel.paint(imagebuf.createGraphics());
    ImageIO.write(imagebuf, "png", file);
  }
}