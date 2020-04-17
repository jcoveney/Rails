package net.sf.rails.util;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
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
    } catch (AWTException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
    }
     panel.paint(imagebuf.createGraphics());
     try {
        ImageIO.write(imagebuf, "png", file);
    } catch (Exception e) {
        // TODO Auto-generated catch block
        System.out.println("error");
    }
  }
}