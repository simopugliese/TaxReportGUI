module com.simonepugliese.taxreportgui {
    requires javafx.controls;
    requires javafx.fxml;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires java.sql;

    // Importante: il modulo backend
    requires TaxReport;

    // Export per lanciare l'app
    exports com.simonepugliese.taxreportgui;

    // Open per JavaFX Reflection (per caricare i Controller)
    opens com.simonepugliese.taxreportgui to javafx.fxml;
    opens com.simonepugliese.taxreportgui.controller to javafx.fxml;
}