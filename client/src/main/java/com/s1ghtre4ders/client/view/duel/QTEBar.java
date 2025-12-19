package com.s1ghtre4ders.client.view.duel;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class QTEBar {

    private static final double BAR_WIDTH  = 400;
    private static final double BAR_HEIGHT = 40;

    // Zones (tune these to taste)
    // Center GREEN zone: 150–250 (no damage)
    // Wider YELLOW zone: 100–300 (half damage)
    private static final double GREEN_START  = 150;
    private static final double GREEN_END    = 250;
    private static final double YELLOW_START = 100;
    private static final double YELLOW_END   = 300;

    private static final double ANIM_TIME_MS = 2000; // 2 seconds

    public interface QTECallback {
        // "NONE" = no damage, "HALF" = half damage, "MISS" = full damage
        void onResult(String quality);
    }

    private final Pane pane = new Pane();
    private final Rectangle background = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
    private final Rectangle yellowZone = new Rectangle(YELLOW_END - YELLOW_START, BAR_HEIGHT);
    private final Rectangle greenZone  = new Rectangle(GREEN_END - GREEN_START, BAR_HEIGHT);
    private final Rectangle marker     = new Rectangle(10, BAR_HEIGHT);

    private final DoubleProperty markerX = new SimpleDoubleProperty(0);

    private Timeline timeline;
    private QTECallback callback;
    private boolean finished = false;

    public QTEBar() {
        // Visuals
        background.setStyle("-fx-fill: #333333;");
        yellowZone.setStyle("-fx-fill: #ffdd00;");
        greenZone.setStyle("-fx-fill: #00dd00;");
        marker.setStyle("-fx-fill: #ffffff; -fx-stroke: #000000; -fx-stroke-width: 2;");

        // Position zones
        yellowZone.setLayoutX(YELLOW_START);
        greenZone.setLayoutX(GREEN_START);

        // Bind marker to animated X property
        marker.layoutXProperty().bind(markerX);
        marker.setLayoutY(0);

        pane.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
        pane.getChildren().addAll(background, yellowZone, greenZone, marker);
        pane.setVisible(false);

        // Attach key handler once, when scene becomes available
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
            }
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
            }
        });
    }

    public Pane getPane() {
        return pane;
    }

    public void start(QTECallback callback) {
        this.callback = callback;
        this.finished = false;
        pane.setVisible(true);

        markerX.set(0);

        if (timeline != null) {
            timeline.stop();
        }

        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(markerX, 0)),
                new KeyFrame(Duration.millis(ANIM_TIME_MS),
                        new KeyValue(markerX, BAR_WIDTH - marker.getWidth()))
        );
        timeline.setCycleCount(1);
        timeline.setOnFinished(e -> {
            if (!finished) {
                System.out.println("⏱️ QTE timeout -> MISS");
                sendResult("MISS");
            }
        });
        timeline.play();
    }

    private void onKey(KeyEvent event) {
        if (!pane.isVisible() || finished) return;
        if (event.getCode() != KeyCode.SPACE) return;

        event.consume();
        if (timeline != null) {
            timeline.stop();
        }

        double center = markerX.get() + marker.getWidth() / 2.0;
        System.out.println("⌨️ QTE SPACE at x=" + center);

        String quality;
        if (center >= GREEN_START && center <= GREEN_END) {
            quality = "NONE"; // no damage
        } else if (center >= YELLOW_START && center <= YELLOW_END) {
            quality = "HALF"; // half damage
        } else {
            quality = "MISS"; // full damage
        }

        System.out.println("⌨️ QTE quality=" + quality);
        sendResult(quality);
    }

    private void sendResult(String quality) {
        if (finished) return;
        finished = true;
        pane.setVisible(false);
        if (callback != null) {
            callback.onResult(quality);
        }
    }

    public void stop() {
        finished = true;
        pane.setVisible(false);
        if (timeline != null) {
            timeline.stop();
        }
    }
}
