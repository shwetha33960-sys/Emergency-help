// Shared navigation: highlight the current page and make the menu work on mobile.
(() => {
  const currentFile = window.location.pathname.split("/").pop() || "index.html";
  document.querySelectorAll(".nav-links a[data-page]").forEach(link => {
    if (link.dataset.page === currentFile) {
      link.classList.add("active");
      link.setAttribute("aria-current", "page");
    }
  });
  const toggle = document.getElementById("menuToggle");
  const links = document.getElementById("navLinks");
  if (toggle && links) {
    toggle.addEventListener("click", () => {
      const open = links.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
    });
  }
})();


function toggleSOSPanel(forceOpen) {
  const panel = document.getElementById("sosPanel");
  const button = document.getElementById("sosButton");
  if (!panel || !button) return;
  const shouldOpen = typeof forceOpen === "boolean" ? forceOpen : panel.hidden;
  panel.hidden = !shouldOpen;
  button.setAttribute("aria-expanded", String(shouldOpen));
}


let currentLatitude = null;
let currentLongitude = null;
let currentAccuracy = null;
const nearbyMap = L.map("nearbyMap").setView([9.9312, 76.2673], 12);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(nearbyMap);
let userMarker = null;
const serviceLayers = {
  hospital: L.layerGroup().addTo(nearbyMap),
  pharmacy: L.layerGroup().addTo(nearbyMap),
  police: L.layerGroup().addTo(nearbyMap)
};
function updateMapWithServices(places, type) {
  const layer = serviceLayers[type];
  if (!layer) return;
  layer.clearLayers();
  places.forEach(place => {
    const lat = place.lat ?? place.center?.lat;
    const lon = place.lon ?? place.center?.lon;
    if (lat == null || lon == null) return;
    const tags = place.tags || {};
    const popup = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = tags.name || `Unnamed ${type}`;
    popup.appendChild(name);
    popup.appendChild(document.createElement("br"));
    popup.appendChild(document.createTextNode(type));
    const markerColors = {
    hospital: "red",
    pharmacy: "green",
    police: "blue"
};

const marker = L.circleMarker([lat, lon], {
    radius: 9,
    color: markerColors[type] || "gray",
    fillColor: markerColors[type] || "gray",
    fillOpacity: 0.85,
    weight: 2
});

marker.bindPopup(popup).addTo(layer);
  });
}
function showMessage(message, type) {
  const result = document.getElementById("locationResult");
  result.className = type;
  result.textContent = message;
}
function findMyLocation() {
  const result = document.getElementById("locationResult");
  result.className = "loading";
  result.textContent = "Finding your location... Please wait.";
  if (!navigator.geolocation) {
    showMessage("Your browser does not support location detection.", "error");
    return;
  }
  navigator.geolocation.getCurrentPosition(position => {
    currentLatitude = position.coords.latitude;
    currentLongitude = position.coords.longitude;
    currentAccuracy = position.coords.accuracy;
    nearbyMap.setView([currentLatitude, currentLongitude], 14);
    if (userMarker) nearbyMap.removeLayer(userMarker);
    userMarker = L.marker([currentLatitude, currentLongitude]).addTo(nearbyMap).bindPopup("Your current location");
    result.className = "success";
    result.innerHTML = "<strong>Location detected successfully!</strong><br>" +
      "Latitude: " + currentLatitude.toFixed(6) + "<br>" +
      "Longitude: " + currentLongitude.toFixed(6) + "<br>" +
      "Estimated accuracy: " + Math.round(currentAccuracy) + " metres";
  }, error => {
    let message = "Unable to detect your location.";
    if (error.code === 1) message = "Location permission denied. Allow location access in your browser.";
    else if (error.code === 2) message = "Your device could not determine your location. Check Windows Location services and Wi-Fi.";
    else if (error.code === 3) message = "Location request timed out. Please try again.";
    showMessage(message, "error");
  }, {enableHighAccuracy:true, timeout:60000, maximumAge:0});
}

async function findService(type) {
    const resultIds = {
        hospital: "hospitalResults",
        pharmacy: "pharmacyResults",
        police: "policeResults"
    };

    const container = document.getElementById(resultIds[type]);

    if (!container) {
        console.error("Results container not found for:", type);
        return;
    }

    if (currentLatitude === null || currentLongitude === null) {
        container.textContent =
            "Please detect your location first.";

        findMyLocation();
        return;
    }

    container.replaceChildren();

    const loading = document.createElement("p");
    loading.textContent = "Searching for nearby " + type + " services...";
    container.appendChild(loading);

    try {
        const url =
            "/api/nearby?lat=" +
            encodeURIComponent(currentLatitude) +
            "&lon=" +
            encodeURIComponent(currentLongitude) +
            "&type=" +
            encodeURIComponent(type);

        const response = await fetch(url);
        const responseText = await response.text();

        let data = {};

        try {
            data = responseText ? JSON.parse(responseText) : {};
        } catch {
            data = { message: responseText };
        }

        if (!response.ok) {
            throw new Error(
                data.detail ||
                data.message ||
                data.error ||
                "Nearby map data is temporarily unavailable."
            );
        }

        if (!Array.isArray(data.elements)) {
            throw new Error("Unexpected response from the server.");
        }

        displayServices(data.elements, container, type);

    } catch (error) {
        console.error("Nearby service search failed:", error);

        container.replaceChildren();

        const message = document.createElement("p");
        message.className = "error";
        message.textContent =
            "We couldn't load nearby " + type +
            " services inside the website. You can search on Google Maps instead.";

        const searchNames = {
            hospital: "hospitals",
            pharmacy: "pharmacies",
            police: "police stations"
        };

        const query =
            searchNames[type] + " near " +
            currentLatitude + "," + currentLongitude;

        const fallback = document.createElement("a");
        fallback.href =
            "https://www.google.com/maps/search/?api=1&query=" +
            encodeURIComponent(query);

        fallback.target = "_blank";
        fallback.rel = "noopener noreferrer";
        fallback.className = "service-map-link";
        fallback.textContent =
            "Search nearby " + searchNames[type] + " on Google Maps";

        container.append(message, fallback);
    }
}

function calculateDistance(lat1, lon1, lat2, lon2) {
  const earthRadius = 6371;
  const toRadians = degrees => degrees * Math.PI / 180;
  const dLat = toRadians(lat2-lat1), dLon = toRadians(lon2-lon1);
  const a = Math.sin(dLat/2)**2 + Math.cos(toRadians(lat1))*Math.cos(toRadians(lat2))*Math.sin(dLon/2)**2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
}
function displayServices(places, container, type) {
  updateMapWithServices(places, type);
  container.replaceChildren();
  if (!places.length) {
    container.textContent = "No mapped " + type + " services found within the searched area.";
    return;
  }
  const sortedPlaces = places.map(place => {
    const latitude = place.lat ?? place.center?.lat;
    const longitude = place.lon ?? place.center?.lon;
    let distance = Infinity;
    if (latitude != null && longitude != null && currentLatitude != null && currentLongitude != null) {
      distance = calculateDistance(currentLatitude,currentLongitude,Number(latitude),Number(longitude));
    }
    return {place,latitude,longitude,distance};
  }).sort((a,b) => a.distance-b.distance);
  const heading = document.createElement("p");
  heading.textContent = "Found " + sortedPlaces.length + " services, sorted from nearest to farthest.";
  container.appendChild(heading);
  sortedPlaces.forEach((result,index) => {
    const place = result.place, tags = place.tags || {};
    const item = document.createElement("div"); item.className = "service-item";
    const name = document.createElement("h3"); name.textContent = (index+1)+". "+(tags.name || "Unnamed "+type); item.appendChild(name);
    const distanceText = document.createElement("p"); distanceText.style.fontWeight="bold"; distanceText.style.color="#176b3a";
    distanceText.textContent = Number.isFinite(result.distance) ? "📍 "+(result.distance<1 ? Math.round(result.distance*1000)+" metres away" : result.distance.toFixed(2)+" km away") : "Distance unavailable";
    item.appendChild(distanceText);
    const addressParts = [tags["addr:housenumber"],tags["addr:street"],tags["addr:suburb"],tags["addr:city"]||tags["addr:town"]||tags["addr:village"],tags["addr:postcode"]].filter(Boolean);
    const address = document.createElement("p"); address.textContent = addressParts.length ? addressParts.join(", ") : "Address not available in map data"; item.appendChild(address);
    if (result.latitude != null && result.longitude != null) {
      const mapLink = document.createElement("a");
      mapLink.href = "https://www.openstreetmap.org/?mlat="+encodeURIComponent(result.latitude)+"&mlon="+encodeURIComponent(result.longitude)+"#map=18/"+result.latitude+"/"+result.longitude;
      mapLink.target="_blank"; mapLink.rel="noopener noreferrer"; mapLink.textContent="View on OpenStreetMap"; item.appendChild(mapLink);
      const directionsLink = document.createElement("a");
      directionsLink.href="https://www.google.com/maps/dir/?api=1&destination="+encodeURIComponent(result.latitude+","+result.longitude);
      directionsLink.target="_blank"; directionsLink.rel="noopener noreferrer"; directionsLink.textContent="Get Directions"; directionsLink.style.marginLeft="15px"; item.appendChild(directionsLink);
    }
    container.appendChild(item);
  });
}


const contactForm = document.getElementById("contactForm");
const contactList = document.getElementById("contactList");
const contactMessage = document.getElementById("contactMessage");
const CONTACTS_API = "/api/contacts";

async function renderContacts() {
  contactList.replaceChildren();
  const loading = document.createElement("p"); loading.textContent = "Loading emergency contacts...";
  contactList.appendChild(loading);
  try {
    const response = await fetch(CONTACTS_API);
    if (!response.ok) throw new Error("Could not load contacts.");
    const contacts = await response.json();
    contactList.replaceChildren();
    if (contacts.length === 0) {
      const emptyMessage = document.createElement("p"); emptyMessage.textContent = "No emergency contacts saved yet.";
      contactList.appendChild(emptyMessage); return;
    }
    contacts.forEach(contact => {
      const card = document.createElement("div"); card.className = "contact-card";
      const name = document.createElement("h3"); name.textContent = contact.name;
      const relation = document.createElement("p"); relation.textContent = "Relationship: " + contact.relation;
      const phone = document.createElement("p"); phone.textContent = "Phone: " + contact.phone;
      const actions = document.createElement("div"); actions.className = "contact-actions";
      const callButton = document.createElement("a"); callButton.className = "call-contact-btn"; callButton.textContent = "📞 Call";
      callButton.href = "tel:" + contact.phone.replace(/[^\d+]/g, "");
      const editButton = document.createElement("button"); editButton.className = "edit-contact-btn"; editButton.textContent = "✏️ Edit"; editButton.type = "button";
      editButton.addEventListener("click", () => editContact(contact));
      const deleteButton = document.createElement("button"); deleteButton.className = "delete-contact-btn"; deleteButton.textContent = "Delete"; deleteButton.type = "button";
      deleteButton.addEventListener("click", () => deleteContact(contact.id));
      actions.append(callButton,editButton,deleteButton); card.append(name,relation,phone,actions); contactList.appendChild(card);
    });
  } catch (error) {
    contactList.replaceChildren();
    const errorMessage = document.createElement("p");
    errorMessage.textContent = "Could not load contacts. Check that your Spring Boot application is running.";
    contactList.appendChild(errorMessage); contactMessage.textContent = error.message;
  }
}

contactForm.addEventListener("submit", async event => {
  event.preventDefault();
  const name = document.getElementById("contactName").value.trim();
  const relation = document.getElementById("contactRelation").value.trim();
  const phone = document.getElementById("contactPhone").value.trim();
  contactMessage.className = ""; contactMessage.textContent = "";
  if (!name || !relation || !phone) { contactMessage.textContent="Please fill in your name, relationship and phone number."; contactMessage.className="error"; return; }
  if (name.length > 100) { contactMessage.textContent="Contact name must not exceed 100 characters."; contactMessage.className="error"; return; }
  if (relation.length > 50) { contactMessage.textContent="Relationship must not exceed 50 characters."; contactMessage.className="error"; return; }
  const digits = phone.replace(/\D/g,"");
  if (!/^\+?[0-9() -]+$/.test(phone) || digits.length < 7 || digits.length > 15) { contactMessage.textContent="Please enter a valid phone number containing 7 to 15 digits."; contactMessage.className="error"; return; }
  const saveButton = contactForm.querySelector('button[type="submit"]');
  saveButton.disabled=true; contactMessage.textContent="Saving your emergency contact..."; contactMessage.className="loading";
  try {
    const response = await fetch(CONTACTS_API,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({name,relation,phone})});
    const responseText = await response.text(); let responseBody = {};
    if (responseText) { try { responseBody=JSON.parse(responseText); } catch { responseBody={message:responseText}; } }
    if (!response.ok) throw new Error(responseBody.detail || responseBody.message || responseBody.title || "The server could not save your contact. Please try again.");
    contactForm.reset(); contactMessage.textContent="Emergency contact saved successfully!"; contactMessage.className="success";
    await renderContacts();
  } catch(error) {
    console.error("Save contact error:",error); contactMessage.textContent="Unable to save contact: "+error.message; contactMessage.className="error";
  } finally { saveButton.disabled=false; }
});

async function deleteContact(contactId) {
  if (!confirm("Are you sure you want to delete this contact?")) return;
  contactMessage.textContent="Deleting contact...";
  try {
    const response = await fetch(CONTACTS_API+"/"+encodeURIComponent(contactId),{method:"DELETE"});
    if (!response.ok) throw new Error("Could not delete contact.");
    contactMessage.textContent="Contact deleted."; await renderContacts();
  } catch(error) { contactMessage.textContent="Delete failed: "+error.message; }
}
async function editContact(contact) {
  const newName=prompt("Enter contact name:",contact.name); if(newName===null)return;
  const newRelation=prompt("Enter relationship:",contact.relation); if(newRelation===null)return;
  const newPhone=prompt("Enter phone number:",contact.phone); if(newPhone===null)return;
  const name=newName.trim(), relation=newRelation.trim(), phone=newPhone.trim(), digits=phone.replace(/\D/g,"");
  if(!name||!relation||!phone){contactMessage.textContent="Please fill in all fields.";return;}
  if(name.length>100||relation.length>50){contactMessage.textContent="Name must be at most 100 characters and relationship at most 50 characters.";return;}
  if(digits.length<7||digits.length>15||! /^[+0-9() -]+$/.test(phone)){contactMessage.textContent="Please enter a valid phone number.";return;}
  contactMessage.textContent="Updating contact...";
  try {
    const response=await fetch(CONTACTS_API+"/"+encodeURIComponent(contact.id),{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({name,relation,phone})});
    if(!response.ok){const errorBody=await response.json().catch(()=>({}));throw new Error(errorBody.detail||errorBody.message||"Could not update contact.");}
    contactMessage.textContent="Contact updated successfully!"; await renderContacts();
  } catch(error) { contactMessage.textContent="Update failed: "+error.message; }
}
renderContacts();



// Single-page navigation: show only the selected section.
function showSection(sectionName) {
  document.querySelectorAll(".app-page").forEach(section => {
    section.hidden = section.id !== "page-" + sectionName;
  });
  document.querySelectorAll(".nav-links [data-section]").forEach(link => {
    const active = link.dataset.section === sectionName;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
  const navLinks = document.getElementById("navLinks");
  const menuToggle = document.getElementById("menuToggle");
  navLinks.classList.remove("open");
  menuToggle.setAttribute("aria-expanded", "false");
  if (sectionName === "nearby" && typeof nearbyMap !== "undefined") {
    setTimeout(() => nearbyMap.invalidateSize(), 80);
  }
  window.scrollTo({ top: 0, behavior: "auto" });
}

document.querySelectorAll("[data-section]").forEach(link => {
  link.addEventListener("click", event => {
    event.preventDefault();
    showSection(link.dataset.section);
  });
});

const menuToggleButton = document.getElementById("menuToggle");
const navigationLinks = document.getElementById("navLinks");
menuToggleButton.addEventListener("click", () => {
  const open = navigationLinks.classList.toggle("open");
  menuToggleButton.setAttribute("aria-expanded", String(open));
});
