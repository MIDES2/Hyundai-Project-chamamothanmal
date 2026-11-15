import React from "react";
import { createRoot } from "react-dom/client";
import NativeVehicleApp from "./NativeVehicleApp.jsx";
import "./vehicle.css";
import "./native-vehicle.css";
createRoot(document.getElementById("root")).render(<React.StrictMode><NativeVehicleApp /></React.StrictMode>);
