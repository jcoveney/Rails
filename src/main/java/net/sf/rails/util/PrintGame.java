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

    GameLoader.loadAndStartGameReturnUI(gameFile).printGameState();
  }

  //TODO this should be based on...literally anything else
  private static File directory = new File("/Users/jcoveney");

  public static void printRoundFacade(String string) {
    try {
      Path writeLocation = new File(directory, "round_facade.txt").toPath();
      log.info("Print round facade [" + string + "] to file: " + writeLocation);
      Files.writeString(writeLocation, string);
    } catch (IOException e) {
      log.info("printRoundFacade", e);
    }
  }

  public static void printGameReport(List<String> actions) {
    Path writeLocation = new File(directory, "game_report.txt").toPath();
    log.info("Print grame report to file: " + writeLocation);
    boolean first = true;
    try (BufferedWriter writer = Files.newBufferedWriter(writeLocation, Charset.forName("UTF-8"))) {
      for (String action : actions) {
        if (first) {
          first = false;
        } else {
          writer.newLine();
        }
        writer.write(action);
      }
      writer.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  public static void printPanel(Scene scene, String _file) {
    File file = new File(directory, _file);
    log.info("printPanel with Scene " + scene + "sent to file" + file);
    WritableImage writableImage = scene.snapshot(null);
    try {
      ImageIO.write(SwingFXUtils.fromFXImage(writableImage, null), "png", file);
    } catch (Exception e) {
      log.info("printPanel", e);
    }
  }

  public static void printPanel(Component panel, String _file) {
    File file = new File(directory, _file);
    log.info("printPanel with Component " + panel + "sent to file" + file);
    //TODO should have some logging
    BufferedImage imagebuf = null;
    try {
        imagebuf = new Robot().createScreenCapture(panel.bounds());
    } catch (AWTException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
     panel.paint(imagebuf.createGraphics());
     try {
        ImageIO.write(imagebuf, "png", file);
    } catch (Exception e) {
        e.printStackTrace();
    }
  }
}