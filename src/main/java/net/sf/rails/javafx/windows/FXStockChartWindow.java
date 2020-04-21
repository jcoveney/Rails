package net.sf.rails.javafx.windows;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.sf.rails.javafx.stockchart.FXStockChart;
import net.sf.rails.ui.swing.GameUIManager;
import net.sf.rails.ui.swing.StatusWindow;
import net.sf.rails.util.PrintGame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;

public class FXStockChartWindow extends Application {
    private static final Logger log = LoggerFactory.getLogger(FXStockChartWindow.class);

    private static GameUIManager gameUIManager;

    private static Stage stage;

    public static void launch(GameUIManager gameUIManager) {
        FXStockChartWindow.gameUIManager = gameUIManager;

        // launch the JavaFX window in a new thread, because the call blocks
        Executors.newSingleThreadExecutor().execute(() -> Application.launch(FXStockChartWindow.class));
    }

    /**
     * Shows/Hides the stock-chart window depending on the given flag
     *
     * @param visible The show/hide flag
     */
    public static void setVisible(boolean visible) {
        Platform.runLater(() -> {
            if (visible) {
                stage.show();
            } else {
                stage.hide();
            }
        });
    }

    @Override
    public void start(Stage primaryStage) {
        // set the stock-chart window to hide, if it has been closed
        Platform.setImplicitExit(false);

        Scene scene = new Scene(new FXStockChart(gameUIManager));

        primaryStage.setTitle("Rails: Stock Chart");
        primaryStage.setScene(scene);

        // uncheck market checkbox in the menu if the stock-chart has been closed
        primaryStage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            StatusWindow.uncheckMenuItemBox(StatusWindow.MARKET_CMD);
        });

        // TODO: save relocation and resizing information of the window

        stage = primaryStage;
        try {
            PrintGame.printPanel(scene, "stock_market.png");
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Exception when printing stock market. Shutting it all down!");
            // This is the worst
            System.exit(1);
        }
    }
}
