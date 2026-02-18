const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.2 });

const revealItems = document.querySelectorAll(".reveal");
revealItems.forEach((item) => observer.observe(item));

const navToggle = document.getElementById("navToggle");
const navLinks = document.getElementById("navLinks");

navToggle.addEventListener("click", () => {
  const isOpen = navLinks.classList.toggle("open");
  navToggle.setAttribute("aria-expanded", String(isOpen));
});

const contactForm = document.getElementById("contactForm");
const formStatus = document.getElementById("formStatus");

navLinks.querySelectorAll("a").forEach((link) => {
  link.addEventListener("click", () => {
    if (navLinks.classList.contains("open")) {
      navLinks.classList.remove("open");
      navToggle.setAttribute("aria-expanded", "false");
    }
  });
});

contactForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const name = document.getElementById("name").value.trim();
  const email = document.getElementById("email").value.trim();
  const message = document.getElementById("message").value.trim();

  const subject = encodeURIComponent("Project inquiry from " + (name || "Website visitor"));
  const bodyLines = [
    "Name: " + (name || "Not provided"),
    "Email: " + (email || "Not provided"),
    "",
    message || "Hi Erick, I'd like to discuss a project."
  ];
  const body = encodeURIComponent(bodyLines.join("\n"));
  const mailto = "mailto:enjuguna794@gmail.com?subject=" + subject + "&body=" + body;

  window.location.href = mailto;

  if (formStatus) {
    formStatus.textContent = "Opening your email app with the message...";
  }
  contactForm.reset();
});
