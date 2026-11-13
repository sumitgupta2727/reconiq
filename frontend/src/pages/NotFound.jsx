import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <div className="notfound">
      <div>
        <div className="notfound-code">404</div>
        <h1 style={{ marginTop: 8, fontSize: "1.5rem" }}>Page not found</h1>
        <p className="muted" style={{ marginTop: 6, marginBottom: 22 }}>
          The page you are looking for does not exist or has been moved.
        </p>
        <Link to="/" className="btn btn-primary">
          <ArrowLeft size={16} />
          Back to dashboard
        </Link>
      </div>
    </div>
  );
}
