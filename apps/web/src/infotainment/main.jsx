import React from "react";
import { createRoot } from "react-dom/client";
import VehicleApp from "./VehicleApp.jsx";
import "./vehicle.css";
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <VehicleApp />
  </React.StrictMode>,
);
