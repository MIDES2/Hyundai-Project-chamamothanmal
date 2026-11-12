export default function Primary({ children, ...props }) {
  return (
    <button className="primary" {...props}>
      {children}
    </button>
  );
}
